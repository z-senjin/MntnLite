package net.runelite.client.plugins.microbot.recovery;

import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.diagnostics.DiagnosticReport;

@Slf4j
public final class StartupRecovery
{
	public static final String DIRECTORY_NAME = "microbot-recovery";
	static final String SAFE_MODE_REQUEST = "safe-mode-next-start";
	static final String SESSION_PREFIX = "session-";
	static final String SESSION_SUFFIX = ".json";
	private static final long EXIT_FLUSH_SECONDS = 2;
	public static final String PROMPT_PROPERTY = "microbot.recovery.prompt";
	static final int MAX_FAILURES = 10;
	private static final Gson GSON = new Gson();
	private static volatile StartupRecovery current;

	public enum Mode
	{
		NORMAL,
		COMMAND_LINE,
		REQUESTED,
		PROMPTED;

		public boolean isSafeMode()
		{
			return this != NORMAL;
		}

		public boolean isTemporary()
		{
			return this == REQUESTED || this == PROMPTED;
		}
	}

	public enum Choice
	{
		START_NORMALLY,
		SAFE_MODE,
		DISABLE_SUSPECTS
	}

	public interface Prompter
	{
		Choice ask(List<String> previousSession, List<String> suspects);
	}

	interface Processes
	{
		long currentPid();

		Long currentStart();

		boolean isAlive(long pid, Long start);
	}

	static final Processes SYSTEM_PROCESSES = new Processes()
	{
		@Override
		public long currentPid()
		{
			return ProcessHandle.current().pid();
		}

		@Override
		public Long currentStart()
		{
			return ProcessHandle.current().info().startInstant().map(i -> i.toEpochMilli()).orElse(null);
		}

		@Override
		public boolean isAlive(long pid, Long start)
		{
			if (pid <= 0 || pid == currentPid())
			{
				return false;
			}
			return ProcessHandle.of(pid)
				.filter(ProcessHandle::isAlive)
				.map(h -> start == null || h.info().startInstant().map(i -> i.toEpochMilli() == start).orElse(true))
				.orElse(false);
		}
	};

	private final Path directory;
	private final Path file;
	private final LongSupplier clock;
	private final Processes processes;
	private final ExecutorService writer;
	private RecoveryState state = new RecoveryState();
	private boolean nextStartSafeMode;
	@Getter
	private List<String> previousSession = Collections.emptyList();
	@Getter
	private Mode mode = Mode.NORMAL;
	@Getter
	private List<String> pendingDisables = Collections.emptyList();
	private boolean writeWarningLogged;

	StartupRecovery(Path directory, LongSupplier clock, Processes processes, ExecutorService writer)
	{
		this.directory = directory;
		this.clock = clock;
		this.processes = processes;
		this.writer = writer;
		this.file = directory.resolve(SESSION_PREFIX + processes.currentPid() + SESSION_SUFFIX);
	}

	public static Mode install(File runeliteDirectory, String microbotVersion, boolean commandLineSafeMode, boolean promptEnabled, Prompter prompter)
	{
		ExecutorService writer = Executors.newSingleThreadExecutor(r ->
		{
			Thread thread = new Thread(r, "microbot-recovery-writer");
			thread.setDaemon(true);
			return thread;
		});
		StartupRecovery recovery = new StartupRecovery(new File(runeliteDirectory, DIRECTORY_NAME).toPath(), System::currentTimeMillis, SYSTEM_PROCESSES, writer);
		Mode mode = recovery.begin(microbotVersion, commandLineSafeMode, promptEnabled, prompter);
		current = recovery;
		Runtime.getRuntime().addShutdownHook(new Thread(recovery::onJvmExit, "microbot-recovery-exit"));
		return mode;
	}

	synchronized Mode begin(String microbotVersion, boolean commandLineSafeMode, boolean promptEnabled, Prompter prompter)
	{
		RecoveryState previous = claimPreviousSession();
		boolean safeModeRequested = consumeSafeModeRequest();
		previousSession = previous.isSessionOpen() ? summarize(previous) : Collections.emptyList();
		if (commandLineSafeMode)
		{
			mode = Mode.COMMAND_LINE;
		}
		else if (safeModeRequested)
		{
			mode = Mode.REQUESTED;
		}
		else if (previous.isSessionOpen() && !previous.isSafeModeSession() && promptEnabled && prompter != null)
		{
			List<String> suspects = suspects(previous);
			Choice choice = ask(prompter, suspects);
			mode = choice == Choice.SAFE_MODE ? Mode.PROMPTED : Mode.NORMAL;
			if (choice == Choice.DISABLE_SUSPECTS)
			{
				pendingDisables = configKeys(previous, suspects);
			}
		}
		else
		{
			mode = Mode.NORMAL;
		}

		state = new RecoveryState();
		state.setSessionOpen(true);
		state.setPid(processes.currentPid());
		state.setProcessStart(processes.currentStart());
		state.setStartedAt(clock.getAsLong());
		state.setMicrobotVersion(microbotVersion);
		state.setSafeModeSession(mode.isSafeMode());
		write(GSON.toJson(state));
		log.info("Startup recovery: mode={}, previous session closed normally={}", mode, !previous.isSessionOpen());
		return mode;
	}

	static List<String> configKeys(RecoveryState previous, List<String> suspects)
	{
		List<String> keys = new ArrayList<>();
		for (String suspect : suspects)
		{
			String key = previous.getConfigKeys().get(suspect);
			key = (key == null || key.isBlank() ? suspect : key).toLowerCase(Locale.ROOT);
			if (key.matches("[a-z0-9_$.-]+") && !keys.contains(key))
			{
				keys.add(key);
			}
		}
		return keys;
	}

	private Choice ask(Prompter prompter, List<String> suspects)
	{
		try
		{
			Choice choice = prompter.ask(previousSession, suspects);
			if (choice == null || (choice == Choice.DISABLE_SUSPECTS && suspects.isEmpty()))
			{
				return Choice.START_NORMALLY;
			}
			return choice;
		}
		catch (RuntimeException e)
		{
			log.warn("Recovery prompt failed, starting normally", e);
			return Choice.START_NORMALLY;
		}
	}

	static List<String> suspects(RecoveryState previous)
	{
		List<String> suspects = new ArrayList<>();
		if (previous.getStartingPlugin() != null)
		{
			suspects.add(previous.getStartingPlugin());
		}
		for (RecoveryState.PluginFailure failure : previous.getFailures())
		{
			if (failure.getPlugin() != null && failure.getPhase() != null && failure.getPhase().contains("start") && !suspects.contains(failure.getPlugin()))
			{
				suspects.add(failure.getPlugin());
			}
		}
		suspects.removeIf(name -> !name.matches("[A-Za-z0-9_$]+"));
		return suspects;
	}

	static List<String> summarize(RecoveryState previous)
	{
		List<String> lines = new ArrayList<>();
		if (previous.getStartupError() != null)
		{
			lines.add("Startup failed: " + DiagnosticReport.clean(previous.getStartupError()));
		}
		if (previous.getStartingPlugin() != null)
		{
			lines.add("Stopped while starting plugin " + DiagnosticReport.clean(previous.getStartingPlugin()));
		}
		for (RecoveryState.PluginFailure failure : previous.getFailures())
		{
			lines.add(DiagnosticReport.clean(failure.getPlugin()) + " " + version(failure.getVersion())
				+ " failed during " + DiagnosticReport.clean(failure.getPhase()) + ": " + DiagnosticReport.clean(failure.getReason()));
		}
		if (lines.isEmpty())
		{
			lines.add("Cause unknown: no plugin or startup error was recorded before the client stopped.");
		}
		if (previous.getActivePlugins().isEmpty())
		{
			lines.add("External plugins running: none recorded");
		}
		else
		{
			StringBuilder running = new StringBuilder("External plugins running: ");
			for (int i = 0; i < previous.getActivePlugins().size(); i++)
			{
				RecoveryState.PluginRecord plugin = previous.getActivePlugins().get(i);
				if (i > 0)
				{
					running.append(", ");
				}
				running.append(DiagnosticReport.clean(plugin.getPlugin())).append(' ').append(version(plugin.getVersion()));
			}
			lines.add(running.toString());
		}
		return lines;
	}

	private static String version(String version)
	{
		String cleaned = DiagnosticReport.clean(version);
		return cleaned.isEmpty() ? "(version unknown)" : "v" + cleaned;
	}

	synchronized void onPluginStarting(String plugin, String configKey)
	{
		state.setStartingPlugin(plugin);
		if (configKey != null)
		{
			state.getConfigKeys().put(plugin, configKey);
		}
		save();
	}

	synchronized void onPluginStarted(String plugin, String version)
	{
		state.setStartingPlugin(null);
		state.getActivePlugins().removeIf(p -> Objects.equals(p.getPlugin(), plugin));
		state.getActivePlugins().add(new RecoveryState.PluginRecord(plugin, version));
		save();
	}

	synchronized void onPluginStopped(String plugin)
	{
		if (state.getActivePlugins().removeIf(p -> Objects.equals(p.getPlugin(), plugin)))
		{
			save();
		}
	}

	synchronized void onPluginFailure(String plugin, String version, String phase, String reason)
	{
		if (Objects.equals(plugin, state.getStartingPlugin()))
		{
			state.setStartingPlugin(null);
		}
		List<RecoveryState.PluginFailure> failures = state.getFailures();
		failures.removeIf(f -> Objects.equals(f.getPlugin(), plugin) && Objects.equals(f.getPhase(), phase));
		failures.add(new RecoveryState.PluginFailure(plugin, version, phase, reason));
		while (failures.size() > MAX_FAILURES)
		{
			failures.remove(0);
		}
		save();
	}

	synchronized void onStartupFailed(Throwable error)
	{
		state.setStartupError(describe(error));
		save();
	}

	synchronized void onCleanExit()
	{
		state.setSessionOpen(false);
		state.setStartingPlugin(null);
		save();
	}

	void onJvmExit()
	{
		Future<?> pending = null;
		String json;
		synchronized (this)
		{
			if (state.getStartupError() == null)
			{
				state.setSessionOpen(false);
				state.setStartingPlugin(null);
			}
			json = GSON.toJson(state);
			if (writer != null)
			{
				try
				{
					pending = writer.submit(() -> write(json));
				}
				catch (RejectedExecutionException e)
				{
					pending = null;
				}
			}
		}
		if (pending == null)
		{
			write(json);
			return;
		}
		try
		{
			pending.get(EXIT_FLUSH_SECONDS, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		catch (ExecutionException | TimeoutException e)
		{
			write(json);
		}
	}

	synchronized void setNextStartSafeMode(boolean enabled)
	{
		nextStartSafeMode = enabled;
		Path request = directory.resolve(SAFE_MODE_REQUEST);
		submit(() ->
		{
			try
			{
				if (enabled)
				{
					Files.createDirectories(directory);
					Files.writeString(request, "1", StandardCharsets.UTF_8);
				}
				else
				{
					Files.deleteIfExists(request);
				}
			}
			catch (IOException e)
			{
				warnOnce(e);
			}
		});
	}

	synchronized boolean isNextStartSafeMode()
	{
		return nextStartSafeMode;
	}

	synchronized RecoveryState snapshot()
	{
		return GSON.fromJson(GSON.toJson(state), RecoveryState.class);
	}

	private RecoveryState claimPreviousSession()
	{
		RecoveryState latest = new RecoveryState();
		if (!Files.isDirectory(directory))
		{
			return latest;
		}
		List<Path> sessions = new ArrayList<>();
		try (Stream<Path> files = Files.list(directory))
		{
			files.filter(StartupRecovery::isSessionFile).forEach(sessions::add);
		}
		catch (IOException e)
		{
			log.warn("Ignoring unreadable startup recovery directory: {}", e.getMessage());
			return latest;
		}
		for (Path session : sessions)
		{
			RecoveryState loaded = read(session);
			if (loaded != null && processes.isAlive(loaded.getPid(), loaded.getProcessStart()))
			{
				continue;
			}
			try
			{
				if (!Files.deleteIfExists(session))
				{
					continue;
				}
			}
			catch (IOException e)
			{
				continue;
			}
			if (loaded != null && loaded.isSessionOpen() && (!latest.isSessionOpen() || loaded.getStartedAt() >= latest.getStartedAt()))
			{
				latest = loaded;
			}
		}
		return latest;
	}

	private static boolean isSessionFile(Path path)
	{
		String name = path.getFileName().toString();
		return name.startsWith(SESSION_PREFIX) && name.endsWith(SESSION_SUFFIX);
	}

	private boolean consumeSafeModeRequest()
	{
		try
		{
			return Files.deleteIfExists(directory.resolve(SAFE_MODE_REQUEST));
		}
		catch (IOException e)
		{
			warnOnce(e);
			return false;
		}
	}

	private static RecoveryState read(Path session)
	{
		try
		{
			RecoveryState loaded = GSON.fromJson(Files.readString(session, StandardCharsets.UTF_8), RecoveryState.class);
			if (loaded == null)
			{
				return null;
			}
			if (loaded.getActivePlugins() == null)
			{
				loaded.setActivePlugins(new ArrayList<>());
			}
			if (loaded.getFailures() == null)
			{
				loaded.setFailures(new ArrayList<>());
			}
			if (loaded.getConfigKeys() == null)
			{
				loaded.setConfigKeys(new HashMap<>());
			}
			return loaded;
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Ignoring unreadable startup recovery state: {}", e.getMessage());
			return null;
		}
	}

	private void save()
	{
		String json = GSON.toJson(state);
		submit(() -> write(json));
	}

	private void submit(Runnable task)
	{
		if (writer == null)
		{
			task.run();
			return;
		}
		try
		{
			writer.execute(task);
		}
		catch (RejectedExecutionException e)
		{
			task.run();
		}
	}

	private void write(String json)
	{
		Path temp = null;
		try
		{
			Files.createDirectories(directory);
			temp = Files.createTempFile(directory, SESSION_PREFIX, ".tmp");
			Files.writeString(temp, json, StandardCharsets.UTF_8);
			try
			{
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			warnOnce(e);
			if (temp != null)
			{
				try
				{
					Files.deleteIfExists(temp);
				}
				catch (IOException ignored)
				{
				}
			}
		}
	}

	private void warnOnce(IOException e)
	{
		if (!writeWarningLogged)
		{
			writeWarningLogged = true;
			log.warn("Could not save startup recovery state: {}", e.getMessage());
		}
	}

	static String describe(Throwable error)
	{
		Throwable root = Throwables.getRootCause(error);
		String message = root.getMessage();
		return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
	}

	public static Mode mode()
	{
		StartupRecovery recovery = current;
		return recovery == null ? Mode.NORMAL : recovery.getMode();
	}

	public static List<String> takePendingDisables()
	{
		StartupRecovery recovery = current;
		if (recovery == null)
		{
			return Collections.emptyList();
		}
		synchronized (recovery)
		{
			List<String> disables = recovery.pendingDisables;
			recovery.pendingDisables = Collections.emptyList();
			return disables;
		}
	}

	public static List<String> previousSessionSummary()
	{
		StartupRecovery recovery = current;
		return recovery == null ? Collections.emptyList() : recovery.getPreviousSession();
	}

	public static void pluginStarting(String plugin, String configKey)
	{
		StartupRecovery recovery = current;
		if (recovery != null)
		{
			recovery.onPluginStarting(plugin, configKey);
		}
	}

	public static void pluginStarted(String plugin, String version)
	{
		StartupRecovery recovery = current;
		if (recovery != null)
		{
			recovery.onPluginStarted(plugin, version);
		}
	}

	public static void pluginStopped(String plugin)
	{
		StartupRecovery recovery = current;
		if (recovery != null)
		{
			recovery.onPluginStopped(plugin);
		}
	}

	public static void pluginFailed(String plugin, String version, String phase, Throwable error)
	{
		pluginFailed(plugin, version, phase, describe(error));
	}

	public static void pluginFailed(String plugin, String version, String phase, String reason)
	{
		StartupRecovery recovery = current;
		if (recovery != null)
		{
			recovery.onPluginFailure(plugin, version, phase, reason);
		}
	}

	public static void startupFailed(Throwable error)
	{
		StartupRecovery recovery = current;
		if (recovery != null)
		{
			recovery.onStartupFailed(error);
		}
	}

	public static void cleanExit()
	{
		StartupRecovery recovery = current;
		if (recovery != null)
		{
			recovery.onCleanExit();
		}
	}

	public static boolean nextStartSafeMode()
	{
		StartupRecovery recovery = current;
		return recovery != null && recovery.isNextStartSafeMode();
	}

	public static boolean requestNextStartSafeMode(boolean enabled)
	{
		StartupRecovery recovery = current;
		if (recovery == null)
		{
			return false;
		}
		recovery.setNextStartSafeMode(enabled);
		return true;
	}
}

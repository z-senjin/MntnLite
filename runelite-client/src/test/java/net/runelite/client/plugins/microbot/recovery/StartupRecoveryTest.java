package net.runelite.client.plugins.microbot.recovery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StartupRecoveryTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Path directory;
	private final AtomicLong clock = new AtomicLong(1000);
	private final AtomicLong pids = new AtomicLong(100);
	private final Set<Long> alive = new HashSet<>();
	private final List<List<String>> prompts = new ArrayList<>();

	@Before
	public void setUp()
	{
		directory = folder.getRoot().toPath().resolve(StartupRecovery.DIRECTORY_NAME);
	}

	private final List<List<String>> suspectPrompts = new ArrayList<>();

	private StartupRecovery.Mode start(boolean commandLine, boolean promptEnabled, boolean answer)
	{
		return start(commandLine, promptEnabled, answer ? StartupRecovery.Choice.SAFE_MODE : StartupRecovery.Choice.START_NORMALLY);
	}

	private StartupRecovery.Mode start(boolean commandLine, boolean promptEnabled, StartupRecovery.Choice answer)
	{
		return session().begin("2.6.25", commandLine, promptEnabled, (lines, suspects) ->
		{
			prompts.add(lines);
			suspectPrompts.add(suspects);
			return answer;
		});
	}

	private StartupRecovery session()
	{
		return session(null);
	}

	private StartupRecovery session(ExecutorService writer)
	{
		long pid = pids.incrementAndGet();
		StartupRecovery.Processes processes = new StartupRecovery.Processes()
		{
			@Override
			public long currentPid()
			{
				return pid;
			}

			@Override
			public Long currentStart()
			{
				return pid * 10;
			}

			@Override
			public boolean isAlive(long other, Long start)
			{
				return other != pid && alive.contains(other) && start != null && start == other * 10;
			}
		};
		last = new StartupRecovery(directory, clock::incrementAndGet, processes, writer);
		return last;
	}

	private long sessionFiles() throws Exception
	{
		try (var files = Files.list(directory))
		{
			return files.filter(p -> p.getFileName().toString().endsWith(".json")).count();
		}
	}

	private StartupRecovery last;

	@Test
	public void firstStartIsNormalWithoutPrompt() throws Exception
	{
		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, true));
		assertTrue(prompts.isEmpty());
		assertTrue(last.snapshot().isSessionOpen());
		assertEquals(1, sessionFiles());
	}

	@Test
	public void cleanExitDoesNotPrompt()
	{
		start(false, true, true);
		last.onCleanExit();
		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, true));
		assertTrue(prompts.isEmpty());
		assertTrue(last.getPreviousSession().isEmpty());
	}

	@Test
	public void uncleanExitPromptsAndSafeModeLastsOneSession()
	{
		start(false, true, true);
		last.onPluginStarted("GotrPlugin", "1.4.2");
		last.onPluginStarted("AutoMiningPlugin", "2.0.0");

		StartupRecovery.Mode mode = start(false, true, true);
		assertEquals(StartupRecovery.Mode.PROMPTED, mode);
		assertTrue(mode.isSafeMode());
		assertTrue(mode.isTemporary());
		assertEquals(1, prompts.size());
		assertTrue(prompts.get(0).get(0).startsWith("Cause unknown"));
		assertEquals("External plugins running: GotrPlugin v1.4.2, AutoMiningPlugin v2.0.0", prompts.get(0).get(1));
		assertTrue(last.snapshot().isSafeModeSession());
		assertTrue(last.snapshot().getActivePlugins().isEmpty());

		last.onCleanExit();
		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, true));
		assertEquals(1, prompts.size());
	}

	@Test
	public void declinedPromptStartsNormally()
	{
		start(false, true, false);
		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, false));
		assertEquals(1, prompts.size());
	}

	@Test
	public void uncleanSafeModeSessionDoesNotPromptAgain()
	{
		start(false, true, true);
		assertEquals(StartupRecovery.Mode.PROMPTED, start(false, true, true));
		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, true));
		assertEquals(1, prompts.size());
	}

	@Test
	public void freezeWhileStartingPluginIsReported()
	{
		start(false, true, false);
		last.onPluginStarted("AutoMiningPlugin", "2.0.0");
		last.onPluginStarting("GotrPlugin", "gotrplugin");

		start(false, true, false);
		assertEquals("Stopped while starting plugin GotrPlugin", prompts.get(0).get(0));
		assertEquals("External plugins running: AutoMiningPlugin v2.0.0", prompts.get(0).get(1));
	}

	@Test
	public void failuresAreReportedWithVersionAndPhase()
	{
		start(false, true, false);
		last.onPluginStarting("BrokenPlugin", "brokenplugin");
		last.onPluginFailure("BrokenPlugin", "1.0.3", "start", StartupRecovery.describe(new IllegalStateException("boom", new NoSuchMethodError("Rs2Walker.walkTo"))));
		last.onPluginFailure("OldPlugin", null, "compatibility check", "requires Microbot 2.7.0 or newer, running 2.6.25");

		RecoveryState state = last.snapshot();
		assertEquals(null, state.getStartingPlugin());
		start(false, true, false);
		List<String> lines = prompts.get(0);
		assertEquals("BrokenPlugin v1.0.3 failed during start: NoSuchMethodError: Rs2Walker.walkTo", lines.get(0));
		assertEquals("OldPlugin (version unknown) failed during compatibility check: requires Microbot 2.7.0 or newer, running 2.6.25", lines.get(1));
		assertEquals("External plugins running: none recorded", lines.get(2));
	}

	@Test
	public void failuresAreDeduplicatedAndBounded()
	{
		start(false, true, false);
		for (int i = 0; i < StartupRecovery.MAX_FAILURES + 5; i++)
		{
			last.onPluginFailure("Plugin" + i, "1", "load", "error");
		}
		last.onPluginFailure("Plugin14", "1", "load", "again");
		List<RecoveryState.PluginFailure> failures = last.snapshot().getFailures();
		assertEquals(StartupRecovery.MAX_FAILURES, failures.size());
		assertEquals("again", failures.get(failures.size() - 1).getReason());
		assertEquals(1, failures.stream().filter(f -> f.getPlugin().equals("Plugin14")).count());
	}

	@Test
	public void startupFailureIsReported()
	{
		start(false, true, false);
		last.onStartupFailed(new RuntimeException("wrapper", new IllegalStateException("Injector failed")));
		start(false, true, false);
		assertEquals("Startup failed: IllegalStateException: Injector failed", prompts.get(0).get(0));
	}

	@Test
	public void summaryExcludesPathsAndIdentifiers()
	{
		start(false, true, false);
		last.onPluginFailure("BrokenPlugin", "1.0", "load", "ZipException: /home/alice/.runelite/microbot-plugins/BrokenPlugin.jar owner alice@example.com");
		start(false, true, false);
		String line = prompts.get(0).get(0);
		assertFalse(line.contains("/home"));
		assertFalse(line.contains("alice"));
		assertTrue(line.contains("[path]"));
	}

	@Test
	public void requestedSafeModeIsConsumedOnce()
	{
		start(false, true, false);
		last.setNextStartSafeMode(true);
		assertTrue(last.isNextStartSafeMode());
		last.onCleanExit();

		StartupRecovery.Mode mode = start(false, true, false);
		assertEquals(StartupRecovery.Mode.REQUESTED, mode);
		assertTrue(mode.isTemporary());
		assertFalse(last.isNextStartSafeMode());
		last.onCleanExit();

		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, false));
		assertTrue(prompts.isEmpty());
	}

	@Test
	public void cancelledRequestStartsNormally()
	{
		start(false, true, false);
		last.setNextStartSafeMode(true);
		last.setNextStartSafeMode(false);
		last.onCleanExit();
		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, false));
	}

	@Test
	public void commandLineSafeModeIsNotTemporaryAndSkipsPrompt()
	{
		start(false, true, false);
		StartupRecovery.Mode mode = start(true, true, false);
		assertEquals(StartupRecovery.Mode.COMMAND_LINE, mode);
		assertTrue(mode.isSafeMode());
		assertFalse(mode.isTemporary());
		assertTrue(prompts.isEmpty());
	}

	@Test
	public void disabledPromptStartsNormally()
	{
		start(false, false, true);
		assertEquals(StartupRecovery.Mode.NORMAL, start(false, false, true));
		assertTrue(prompts.isEmpty());
	}

	@Test
	public void failingPrompterStartsNormally()
	{
		start(false, true, false);
		StartupRecovery.Mode mode = session().begin("2.6.25", false, true, (lines, suspects) ->
		{
			throw new IllegalStateException("no display");
		});
		assertEquals(StartupRecovery.Mode.NORMAL, mode);
	}

	@Test
	public void unreadableStateStartsNormally() throws Exception
	{
		Files.createDirectories(directory);
		Files.writeString(directory.resolve("session-1.json"), "{not json", StandardCharsets.UTF_8);
		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, true));
		assertTrue(prompts.isEmpty());
		assertTrue(last.snapshot().isSessionOpen());
	}

	@Test
	public void stoppedPluginIsNotReportedAsRunning()
	{
		start(false, true, false);
		last.onPluginStarted("GotrPlugin", "1.4.2");
		last.onPluginStopped("GotrPlugin");
		start(false, true, false);
		assertEquals("External plugins running: none recorded", prompts.get(0).get(1));
	}

	@Test
	public void frozenPluginCanBeDisabledForNormalStart()
	{
		start(false, true, false);
		last.onPluginStarting("M06HangPlugin", null);

		StartupRecovery.Mode mode = start(false, true, StartupRecovery.Choice.DISABLE_SUSPECTS);
		assertEquals(StartupRecovery.Mode.NORMAL, mode);
		assertEquals(List.of("M06HangPlugin"), suspectPrompts.get(0));
		assertEquals(List.of("m06hangplugin"), last.getPendingDisables());
	}

	@Test
	public void startFailuresAreSuspectsButCompatibilitySkipsAreNot()
	{
		RecoveryState state = new RecoveryState();
		state.setStartingPlugin("HangPlugin");
		state.getFailures().add(new RecoveryState.PluginFailure("ThrowPlugin", "1", "start", "x"));
		state.getFailures().add(new RecoveryState.PluginFailure("OldPlugin", "1", "compatibility check", "x"));
		state.getFailures().add(new RecoveryState.PluginFailure("HubPlugin", "1", "load or start", "x"));
		state.getFailures().add(new RecoveryState.PluginFailure("../evil", "1", "start", "x"));
		assertEquals(List.of("HangPlugin", "ThrowPlugin", "HubPlugin"), StartupRecovery.suspects(state));
	}

	@Test
	public void disableWithoutSuspectsStartsNormallyWithoutDisables()
	{
		start(false, true, false);
		StartupRecovery.Mode mode = start(false, true, StartupRecovery.Choice.DISABLE_SUSPECTS);
		assertEquals(StartupRecovery.Mode.NORMAL, mode);
		assertTrue(suspectPrompts.get(0).isEmpty());
		assertTrue(last.getPendingDisables().isEmpty());
	}

	@Test
	public void disableUsesDescriptorConfigKey()
	{
		start(false, true, false);
		last.onPluginStarting("FancyFighterPlugin", "aiofighter");

		start(false, true, StartupRecovery.Choice.DISABLE_SUSPECTS);
		assertEquals(List.of("FancyFighterPlugin"), suspectPrompts.get(0));
		assertEquals(List.of("aiofighter"), last.getPendingDisables());
	}

	@Test
	public void secondClientDoesNotTreatRunningClientAsCrashed() throws Exception
	{
		start(false, true, true);
		StartupRecovery first = last;
		alive.add(pids.get());
		first.onPluginStarted("GotrPlugin", "1.4.2");

		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, true));
		StartupRecovery second = last;
		assertTrue(prompts.isEmpty());
		assertTrue(second.getPreviousSession().isEmpty());
		assertEquals(2, sessionFiles());

		second.onCleanExit();
		assertTrue(first.snapshot().isSessionOpen());
		assertEquals(List.of(new RecoveryState.PluginRecord("GotrPlugin", "1.4.2")), first.snapshot().getActivePlugins());

		alive.clear();
		assertEquals(StartupRecovery.Mode.PROMPTED, start(false, true, true));
		assertEquals(1, prompts.size());
		assertEquals("External plugins running: GotrPlugin v1.4.2", prompts.get(0).get(1));
		assertEquals(1, sessionFiles());
	}

	@Test
	public void safeModeRequestFromOneClientIsConsumedByNextStartOnly() throws Exception
	{
		start(false, true, false);
		StartupRecovery first = last;
		alive.add(pids.get());
		start(false, true, false);
		StartupRecovery second = last;
		alive.add(pids.get());

		second.setNextStartSafeMode(true);
		first.onCleanExit();
		assertTrue(Files.exists(directory.resolve(StartupRecovery.SAFE_MODE_REQUEST)));

		assertEquals(StartupRecovery.Mode.REQUESTED, start(false, true, false));
		alive.add(pids.get());
		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, false));
		assertTrue(prompts.isEmpty());
	}

	@Test
	public void reusedPidOfDeadClientIsNotTreatedAsRunning() throws Exception
	{
		start(false, true, false);
		long pid = pids.get();
		alive.add(pid);
		Path old = directory.resolve(StartupRecovery.SESSION_PREFIX + pid + StartupRecovery.SESSION_SUFFIX);
		Files.writeString(old, Files.readString(old).replace("\"processStart\":" + pid * 10, "\"processStart\":1"));

		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, false));
		assertEquals(1, prompts.size());
	}

	@Test
	public void concurrentWritersUseUniqueTempFiles() throws Exception
	{
		ExecutorService a = Executors.newSingleThreadExecutor();
		ExecutorService b = Executors.newSingleThreadExecutor();
		try
		{
			session(a).begin("2.6.25", false, false, null);
			StartupRecovery first = last;
			alive.add(pids.get());
			session(b).begin("2.6.25", false, false, null);
			StartupRecovery second = last;
			for (int i = 0; i < 200; i++)
			{
				first.onPluginStarted("A" + (i % 5), "1");
				second.onPluginStarted("B" + (i % 5), "1");
			}
		}
		finally
		{
			a.shutdown();
			b.shutdown();
			assertTrue(a.awaitTermination(10, TimeUnit.SECONDS));
			assertTrue(b.awaitTermination(10, TimeUnit.SECONDS));
		}
		try (var files = Files.list(directory))
		{
			assertEquals(0, files.filter(p -> p.getFileName().toString().endsWith(".tmp")).count());
		}
		assertEquals(2, sessionFiles());
		alive.clear();
		start(false, true, false);
		assertEquals(1, prompts.size());
	}

	@Test
	public void writesOnBackgroundExecutorKeepOrder() throws Exception
	{
		ExecutorService writer = Executors.newSingleThreadExecutor();
		session(writer).begin("2.6.25", false, false, null);
		StartupRecovery first = last;
		first.onPluginStarting("GotrPlugin", "gotrplugin");
		first.onPluginStarted("GotrPlugin", "1.4.2");
		first.onPluginStopped("GotrPlugin");
		first.onPluginStarting("HangPlugin", "hangplugin");
		writer.shutdown();
		assertTrue(writer.awaitTermination(10, TimeUnit.SECONDS));

		start(false, true, false);
		assertEquals("Stopped while starting plugin HangPlugin", prompts.get(0).get(0));
		assertEquals("External plugins running: none recorded", prompts.get(0).get(1));
	}

	@Test
	public void jvmExitWithoutWindowCloseIsClean()
	{
		ExecutorService writer = Executors.newSingleThreadExecutor();
		session(writer).begin("2.6.25", false, true, null);
		last.onPluginStarted("BreakHandlerPlugin", "1.0");
		last.onJvmExit();
		writer.shutdownNow();

		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, true));
		assertTrue(prompts.isEmpty());
		assertTrue(last.getPreviousSession().isEmpty());
	}

	@Test
	public void jvmExitAfterWriterStoppedStillMarksClean()
	{
		ExecutorService writer = Executors.newSingleThreadExecutor();
		session(writer).begin("2.6.25", false, true, null);
		writer.shutdownNow();
		last.onJvmExit();

		assertEquals(StartupRecovery.Mode.NORMAL, start(false, true, true));
		assertTrue(prompts.isEmpty());
	}

	@Test
	public void jvmExitAfterStartupFailureStillPrompts()
	{
		start(false, true, false);
		last.onStartupFailed(new IllegalStateException("Injector failed"));
		last.onJvmExit();

		start(false, true, false);
		assertEquals(1, prompts.size());
		assertEquals("Startup failed: IllegalStateException: Injector failed", prompts.get(0).get(0));
	}

	@Test
	public void testModeDisablesPrompt()
	{
		String previous = System.getProperty("microbot.test.mode");
		System.setProperty("microbot.test.mode", "true");
		try
		{
			assertFalse(RecoveryPrompt.isEnabled());
		}
		finally
		{
			if (previous == null)
			{
				System.clearProperty("microbot.test.mode");
			}
			else
			{
				System.setProperty("microbot.test.mode", previous);
			}
		}
	}
}

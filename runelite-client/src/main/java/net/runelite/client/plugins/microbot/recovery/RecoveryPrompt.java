package net.runelite.client.plugins.microbot.recovery;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.plugins.microbot.diagnostics.BuildProvenance;
import net.runelite.client.plugins.microbot.diagnostics.DiagnosticReport;

@Slf4j
public final class RecoveryPrompt implements StartupRecovery.Prompter
{
	public static final String TIMEOUT_PROPERTY = "microbot.recovery.promptSeconds";
	static final int DEFAULT_TIMEOUT_SECONDS = 20;

	public static boolean isEnabled()
	{
		return !GraphicsEnvironment.isHeadless()
			&& !Boolean.getBoolean("microbot.test.mode")
			&& !"false".equalsIgnoreCase(System.getProperty(StartupRecovery.PROMPT_PROPERTY));
	}

	@Override
	public StartupRecovery.Choice ask(List<String> previousSession, List<String> suspects)
	{
		AtomicReference<StartupRecovery.Choice> choice = new AtomicReference<>(StartupRecovery.Choice.START_NORMALLY);
		try
		{
			SwingUtilities.invokeAndWait(() -> show(previousSession, suspects, choice));
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		catch (InvocationTargetException e)
		{
			log.warn("Recovery prompt failed, starting normally", e.getCause());
		}
		return choice.get();
	}

	private static void show(List<String> previousSession, List<String> suspects, AtomicReference<StartupRecovery.Choice> choice)
	{
		JDialog dialog = new JDialog((java.awt.Frame) null, "Microbot recovery", true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setAlwaysOnTop(true);

		JPanel body = new JPanel(new BorderLayout(0, 8));
		body.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		body.add(new JLabel("<html><body style='width:440px'><b>Microbot did not close normally last time.</b><br>"
			+ "It may have frozen, crashed or been force-closed. Safe mode starts without external (Hub or sideloaded) plugins "
			+ "and the GPU plugin for this session only. Your plugin files and settings are kept, and the next start is normal. "
			+ (suspects.isEmpty() ? "" : "Disabling a plugin turns it off in the plugin list until you turn it back on.") + "</html>"),
			BorderLayout.NORTH);

		JTextArea details = new JTextArea(String.join("\n", previousSession), Math.min(8, previousSession.size() + 1), 50);
		details.setEditable(false);
		details.setLineWrap(true);
		details.setWrapStyleWord(true);
		body.add(new JScrollPane(details), BorderLayout.CENTER);

		JLabel countdown = new JLabel();
		JButton safe = new JButton("Start in safe mode");
		JButton normal = new JButton("Start normally");
		JButton copy = new JButton("Copy details");
		JButton disable = new JButton(disableText(suspects));
		disable.setToolTipText("Turns these plugins off in the plugin list and starts with your other plugins. Files are kept; turn them back on in the plugin list.");
		int seconds = timeoutSeconds();
		int[] remaining = {seconds};
		countdown.setText(countdownText(remaining[0]));
		Timer timer = new Timer(1000, null);
		timer.addActionListener(e ->
		{
			remaining[0]--;
			if (remaining[0] <= 0)
			{
				timer.stop();
				dialog.dispose();
			}
			else
			{
				countdown.setText(countdownText(remaining[0]));
			}
		});

		disable.addActionListener(e ->
		{
			timer.stop();
			choice.set(StartupRecovery.Choice.DISABLE_SUSPECTS);
			dialog.dispose();
		});
		safe.addActionListener(e ->
		{
			timer.stop();
			choice.set(StartupRecovery.Choice.SAFE_MODE);
			dialog.dispose();
		});
		normal.addActionListener(e ->
		{
			timer.stop();
			dialog.dispose();
		});
		copy.addActionListener(e ->
		{
			timer.stop();
			countdown.setText("Countdown paused");
			try
			{
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(copyText(previousSession)), null);
				copy.setText("Copied");
			}
			catch (RuntimeException ex)
			{
				log.warn("Could not copy recovery details: {}", ex.getMessage());
				copy.setText("Copy failed");
			}
		});

		JPanel south = new JPanel(new BorderLayout());
		south.add(countdown, BorderLayout.WEST);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.add(copy);
		buttons.add(normal);
		if (!suspects.isEmpty())
		{
			buttons.add(disable);
		}
		buttons.add(safe);
		south.add(buttons, BorderLayout.EAST);
		body.add(south, BorderLayout.SOUTH);

		dialog.setContentPane(body);
		dialog.getRootPane().setDefaultButton(normal);
		dialog.pack();
		dialog.setLocationRelativeTo(null);
		timer.start();
		dialog.setVisible(true);
		timer.stop();
	}

	static String disableText(List<String> suspects)
	{
		String names = suspects.size() == 1 ? suspects.get(0) : suspects.size() + " plugins";
		return "Disable " + names + " and start";
	}

	static String countdownText(int seconds)
	{
		return "Starting normally in " + seconds + "s";
	}

	static String copyText(List<String> previousSession)
	{
		BuildProvenance provenance = BuildProvenance.resolve(RuneLiteProperties.getMicrobotCommit(), RuneLiteProperties.getCommit(),
			RuneLiteProperties.isDirty(), RuneLiteProperties.getMicrobotBuildChannel(), RuneLiteProperties.getMicrobotBuildRepository());
		StringBuilder out = new StringBuilder("Microbot recovery details\n");
		out.append("Microbot: ").append(orUnknown(RuneLiteProperties.getMicrobotVersion())).append('\n');
		out.append("Commit: ").append(DiagnosticReport.clean(provenance.getCommit())).append('\n');
		out.append("Build origin: ").append(DiagnosticReport.clean(provenance.getOrigin())).append('\n');
		out.append("Java: ").append(orUnknown(System.getProperty("java.version"))).append('\n');
		out.append("OS: ").append(orUnknown(System.getProperty("os.name"))).append(' ').append(orUnknown(System.getProperty("os.arch"))).append('\n');
		out.append("Previous session:\n");
		for (String line : previousSession)
		{
			out.append("- ").append(line).append('\n');
		}
		out.append("After startup, use Copy diagnostics in the Microbot panel for the full report.\n");
		return out.toString();
	}

	private static String orUnknown(String value)
	{
		String cleaned = DiagnosticReport.clean(value);
		return cleaned.isEmpty() ? "unknown" : cleaned;
	}

	static int timeoutSeconds()
	{
		try
		{
			int value = Integer.parseInt(System.getProperty(TIMEOUT_PROPERTY, String.valueOf(DEFAULT_TIMEOUT_SECONDS)));
			return Math.max(1, Math.min(300, value));
		}
		catch (NumberFormatException e)
		{
			return DEFAULT_TIMEOUT_SECONDS;
		}
	}
}

package net.runelite.client.ui;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsoleLogAppenderTest
{
	private static final String CONFIG = "<configuration>"
		+ "<appender name=\"STDOUT\" class=\"ch.qos.logback.core.ConsoleAppender\">"
		+ "<encoder><pattern>%msg%n</pattern></encoder>"
		+ "</appender>"
		+ "<root level=\"INFO\"><appender-ref ref=\"STDOUT\"/></root>"
		+ "</configuration>";

	private static final String NO_STDOUT_CONFIG = "<configuration><root level=\"INFO\"/></configuration>";

	private PrintStream originalOut;
	private LoggerContext context;

	@Before
	public void setUp()
	{
		originalOut = System.out;
		context = new LoggerContext();
	}

	@After
	public void tearDown()
	{
		System.setOut(originalOut);
		context.stop();
	}

	@Test
	public void logLineReachesTheConsoleOnceAndStdoutOnce()
	{
		ByteArrayOutputStream terminal = new ByteArrayOutputStream();
		ByteArrayOutputStream consolePanel = new ByteArrayOutputStream();
		StringBuilder console = new StringBuilder();

		Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
		PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext(context);
		encoder.setPattern("%msg%n");
		encoder.start();
		ConsoleAppender<ILoggingEvent> stdout = new ConsoleAppender<>();
		stdout.setContext(context);
		stdout.setEncoder(encoder);
		stdout.start();
		root.addAppender(stdout);

		System.setOut(new PrintStream(new Tee(consolePanel, terminal), true, StandardCharsets.UTF_8));

		ConsoleLogAppender.bindStdoutAppenders(context, terminal);
		ConsoleLogAppender appender = new ConsoleLogAppender(console::append);
		appender.setContext(context);
		appender.start();
		root.addAppender(appender);

		root.info("flood line");
		System.out.println("plain print");

		String panel = console + consolePanel.toString(StandardCharsets.UTF_8);
		assertEquals(1, occurrences(panel, "flood line"));
		assertEquals(1, occurrences(panel, "plain print"));

		String out = terminal.toString(StandardCharsets.UTF_8);
		assertEquals(1, occurrences(out, "flood line"));
		assertEquals(1, occurrences(out, "plain print"));
	}

	@Test
	public void stoppingTheLoggerContextLeavesStdoutOpen()
	{
		CloseTrackingStream terminal = new CloseTrackingStream();

		Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
		PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext(context);
		encoder.setPattern("%msg%n");
		encoder.start();
		ConsoleAppender<ILoggingEvent> stdout = new ConsoleAppender<>();
		stdout.setContext(context);
		stdout.setEncoder(encoder);
		stdout.start();
		root.addAppender(stdout);

		ConsoleLogAppender.bindStdoutAppenders(context, terminal);
		root.info("before stop");
		context.stop();

		assertFalse(terminal.closed);
		assertEquals(1, occurrences(terminal.toString(StandardCharsets.UTF_8), "before stop"));
	}

	@Test
	public void reconfigurationKeepsTheConsoleAppenderAndStdoutBinding() throws JoranException
	{
		ByteArrayOutputStream terminal = new ByteArrayOutputStream();
		ByteArrayOutputStream consolePanel = new ByteArrayOutputStream();
		StringBuilder console = new StringBuilder();

		configure();
		System.setOut(new PrintStream(new Tee(consolePanel, terminal), true, StandardCharsets.UTF_8));
		ConsoleLogAppender.install(context, terminal, console::append);

		context.reset();
		configure();
		context.getLogger(Logger.ROOT_LOGGER_NAME).info("after reset");

		assertEquals(1, occurrences(console.toString(), "after reset"));
		assertEquals(0, occurrences(consolePanel.toString(StandardCharsets.UTF_8), "after reset"));
		assertEquals(1, occurrences(terminal.toString(StandardCharsets.UTF_8), "after reset"));
	}

	@Test
	public void lineLoggedBeforeReconfigurationEndsDoesNotLeaveStdoutUnbound() throws JoranException
	{
		ByteArrayOutputStream terminal = new ByteArrayOutputStream();
		ByteArrayOutputStream consolePanel = new ByteArrayOutputStream();
		StringBuilder console = new StringBuilder();

		configure();
		System.setOut(new PrintStream(new Tee(consolePanel, terminal), true, StandardCharsets.UTF_8));
		ConsoleLogAppender.install(context, terminal, console::append);

		context.reset();
		Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
		root.info("during reset");
		configure();
		root.info("after reset");

		assertEquals(1, occurrences(console.toString(), "during reset"));
		assertEquals(1, occurrences(console.toString(), "after reset"));
		assertEquals(0, occurrences(consolePanel.toString(StandardCharsets.UTF_8), "after reset"));
		assertEquals(1, occurrences(terminal.toString(StandardCharsets.UTF_8), "after reset"));
	}

	@Test
	public void stoppingTheLoggerContextDetachesTheConsoleAppender()
	{
		ConsoleLogAppender appender = ConsoleLogAppender.install(context, new ByteArrayOutputStream(), line -> { });

		context.stop();

		assertFalse(appender.isStarted());
		assertFalse(context.getLogger(Logger.ROOT_LOGGER_NAME).isAttached(appender));
	}

	@Test
	public void reconfigurationWithoutStdoutStopsSearchingAfterBoundedLines() throws JoranException
	{
		configure();
		ConsoleLogAppender appender = ConsoleLogAppender.install(context, new ByteArrayOutputStream(), line -> { });

		context.reset();
		configure(NO_STDOUT_CONFIG);
		Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
		for (int i = 0; i < ConsoleLogAppender.MAX_REBIND_ATTEMPTS - 1; i++)
		{
			root.info("line {}", i);
		}
		assertTrue(appender.isRebindPending());

		root.info("last line");

		assertFalse(appender.isRebindPending());
	}

	@Test
	public void uninstallRemovesTheResetListener() throws JoranException
	{
		StringBuilder console = new StringBuilder();
		configure();
		ConsoleLogAppender.install(context, new ByteArrayOutputStream(), console::append).uninstall();

		context.reset();
		configure();
		context.getLogger(Logger.ROOT_LOGGER_NAME).info("after uninstall");

		assertTrue(context.getCopyOfListenerList().isEmpty());
		assertEquals(0, occurrences(console.toString(), "after uninstall"));
	}

	private void configure() throws JoranException
	{
		configure(CONFIG);
	}

	private void configure(String config) throws JoranException
	{
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(context);
		configurator.doConfigure(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));
	}

	private static int occurrences(String text, String needle)
	{
		int count = 0;
		for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1))
		{
			count++;
		}
		return count;
	}

	private static final class CloseTrackingStream extends ByteArrayOutputStream
	{
		private boolean closed;

		@Override
		public void close()
		{
			closed = true;
		}
	}

	private static final class Tee extends OutputStream
	{
		private final OutputStream first;
		private final OutputStream second;

		private Tee(OutputStream first, OutputStream second)
		{
			this.first = first;
			this.second = second;
		}

		@Override
		public void write(int b) throws IOException
		{
			first.write(b);
			second.write(b);
		}
	}
}

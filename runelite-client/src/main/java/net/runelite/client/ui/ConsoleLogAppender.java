/*
 * Copyright (c) 2024 Microbot Contributors
 * All rights reserved.
 */
package net.runelite.client.ui;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.spi.ConsoleTarget;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.function.Consumer;
import net.runelite.client.util.ResetResistantAttachment;

final class ConsoleLogAppender extends AppenderBase<ILoggingEvent>
{
	static final int MAX_REBIND_ATTEMPTS = 1000;

	private final Consumer<String> logConsumer;
	private final OutputStream stdout;
	private PatternLayout layout;
	private ResetResistantAttachment attachment;
	private int rebindAttemptsLeft;

	ConsoleLogAppender(Consumer<String> logConsumer)
	{
		this(logConsumer, null);
	}

	private ConsoleLogAppender(Consumer<String> logConsumer, OutputStream stdout)
	{
		this.logConsumer = logConsumer;
		this.stdout = stdout;
	}

	static ConsoleLogAppender install(LoggerContext context, OutputStream stdout, Consumer<String> logConsumer)
	{
		ConsoleLogAppender appender = new ConsoleLogAppender(logConsumer, stdout);
		appender.setContext(context);
		bindStdoutAppenders(context, stdout);
		appender.start();
		appender.attachment = ResetResistantAttachment.attach(context, appender, appender::restartAfterReset);
		return appender;
	}

	void uninstall()
	{
		attachment.remove();
	}

	static boolean bindStdoutAppenders(LoggerContext context, OutputStream stdout)
	{
		OutputStream shielded = new CloseShieldOutputStream(stdout);
		boolean bound = false;
		for (Logger logger : context.getLoggerList())
		{
			Iterator<Appender<ILoggingEvent>> appenders = logger.iteratorForAppenders();
			while (appenders.hasNext())
			{
				Appender<ILoggingEvent> appender = appenders.next();
				if (appender instanceof ConsoleAppender
					&& ConsoleTarget.SystemOut.getName().equals(((ConsoleAppender<ILoggingEvent>) appender).getTarget()))
				{
					((ConsoleAppender<ILoggingEvent>) appender).setOutputStream(shielded);
					bound = true;
				}
			}
		}
		return bound;
	}

	private synchronized void restartAfterReset()
	{
		rebindAttemptsLeft = stdout != null ? MAX_REBIND_ATTEMPTS : 0;
		start();
	}

	synchronized boolean isRebindPending()
	{
		return rebindAttemptsLeft > 0;
	}

	@Override
	public void start()
	{
		layout = new PatternLayout();
		layout.setContext(getContext());
		layout.setPattern("%d{HH:mm:ss} %-5level %logger{36} - %msg%n");
		layout.start();
		super.start();
	}

	@Override
	protected void append(ILoggingEvent eventObject)
	{
		if (!isStarted())
		{
			return;
		}
		if (rebindAttemptsLeft > 0)
		{
			rebindAttemptsLeft = bindStdoutAppenders((LoggerContext) getContext(), stdout) ? 0 : rebindAttemptsLeft - 1;
		}
		String formatted = layout.doLayout(eventObject);
		logConsumer.accept(formatted);
	}

	private static final class CloseShieldOutputStream extends FilterOutputStream
	{
		private CloseShieldOutputStream(OutputStream out)
		{
			super(out);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException
		{
			out.write(b, off, len);
		}

		@Override
		public void close() throws IOException
		{
			flush();
		}
	}
}

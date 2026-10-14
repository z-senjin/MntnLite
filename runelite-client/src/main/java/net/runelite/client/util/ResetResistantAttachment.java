/*
 * Copyright (c) 2024 Microbot Contributors
 * All rights reserved.
 */
package net.runelite.client.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.status.ErrorStatus;

public final class ResetResistantAttachment implements LoggerContextListener
{
	private final LoggerContext context;
	private final Appender<ILoggingEvent> appender;
	private final Runnable onReset;
	private volatile boolean removed;

	private ResetResistantAttachment(LoggerContext context, Appender<ILoggingEvent> appender, Runnable onReset)
	{
		this.context = context;
		this.appender = appender;
		this.onReset = onReset;
	}

	public static ResetResistantAttachment attach(LoggerContext context, Appender<ILoggingEvent> appender, Runnable onReset)
	{
		ResetResistantAttachment attachment = new ResetResistantAttachment(context, appender, onReset);
		context.addListener(attachment);
		root(context).addAppender(appender);
		return attachment;
	}

	public void remove()
	{
		removed = true;
		context.removeListener(this);
		root(context).detachAppender(appender);
		appender.stop();
	}

	@Override
	public boolean isResetResistant()
	{
		return true;
	}

	@Override
	public void onStart(LoggerContext context)
	{
	}

	@Override
	public void onReset(LoggerContext context)
	{
		if (removed)
		{
			return;
		}
		try
		{
			onReset.run();
		}
		catch (RuntimeException e)
		{
			context.getStatusManager().add(new ErrorStatus("Failed to restart appender " + appender.getName() + " after reset", this, e));
		}
		root(context).addAppender(appender);
	}

	@Override
	public void onStop(LoggerContext context)
	{
		removed = true;
		root(context).detachAppender(appender);
		appender.stop();
	}

	@Override
	public void onLevelChange(Logger logger, Level level)
	{
	}

	private static Logger root(LoggerContext context)
	{
		return context.getLogger(Logger.ROOT_LOGGER_NAME);
	}
}

package net.runelite.client.util;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.read.ListAppender;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResetResistantAttachmentTest
{
	private static final String CONFIG = "<configuration>"
		+ "<appender name=\"LIST\" class=\"ch.qos.logback.core.read.ListAppender\"/>"
		+ "<root level=\"INFO\"><appender-ref ref=\"LIST\"/></root>"
		+ "</configuration>";

	private LoggerContext context;
	private ListAppender<ILoggingEvent> appender;

	@Before
	public void setUp() throws JoranException
	{
		context = new LoggerContext();
		configure();
		appender = new ListAppender<>();
		appender.setContext(context);
		appender.start();
	}

	@After
	public void tearDown()
	{
		context.stop();
	}

	@Test
	public void appenderIsReattachedOnceAfterReconfiguration() throws JoranException
	{
		ResetResistantAttachment.attach(context, appender, appender::start);

		context.reset();
		configure();
		root().info("after reset");

		assertTrue(appender.isStarted());
		assertEquals(1, attachedCount());
		assertEquals(1, appender.list.size());
	}

	@Test
	public void appenderIsReattachedAfterEveryReset() throws JoranException
	{
		ResetResistantAttachment.attach(context, appender, appender::start);

		for (int i = 0; i < 3; i++)
		{
			context.reset();
			configure();
		}
		root().info("after resets");

		assertEquals(1, attachedCount());
		assertEquals(1, appender.list.size());
	}

	@Test
	public void removedAttachmentLeavesNoListenerAndIsNotReattached() throws JoranException
	{
		ResetResistantAttachment attachment = ResetResistantAttachment.attach(context, appender, appender::start);

		attachment.remove();
		context.reset();
		configure();
		root().info("after reset");

		assertFalse(context.getCopyOfListenerList().contains(attachment));
		assertFalse(appender.isStarted());
		assertEquals(0, attachedCount());
		assertEquals(0, appender.list.size());
	}

	@Test
	public void reattachingAfterRemoveKeepsOneListenerAndOneAttachment() throws JoranException
	{
		ResetResistantAttachment.attach(context, appender, appender::start).remove();
		appender.start();
		ResetResistantAttachment.attach(context, appender, appender::start);

		context.reset();
		configure();
		root().info("after restart and reset");

		assertEquals(1, attachmentListeners());
		assertEquals(1, attachedCount());
		assertEquals(1, appender.list.size());
	}

	@Test
	public void failingRestartStillReattachesAndLetsOtherListenersRun() throws JoranException
	{
		ResetResistantAttachment.attach(context, appender, () ->
		{
			throw new IllegalStateException("executor rejected");
		});
		ListAppender<ILoggingEvent> other = new ListAppender<>();
		other.setContext(context);
		other.start();
		ResetResistantAttachment.attach(context, other, other::start);

		context.reset();
		configure();
		root().info("after reset");

		assertEquals(1, attachedCount());
		assertEquals(1, other.list.size());
	}

	@Test
	public void stoppingTheContextDetachesAndStopsTheAppender()
	{
		ResetResistantAttachment.attach(context, appender, appender::start);

		context.stop();

		assertFalse(appender.isStarted());
		assertEquals(0, attachedCount());
	}

	private int attachedCount()
	{
		int count = 0;
		Iterator<Appender<ILoggingEvent>> appenders = root().iteratorForAppenders();
		while (appenders.hasNext())
		{
			if (appenders.next() == appender)
			{
				count++;
			}
		}
		return count;
	}

	private int attachmentListeners()
	{
		int count = 0;
		for (LoggerContextListener listener : context.getCopyOfListenerList())
		{
			if (listener instanceof ResetResistantAttachment)
			{
				count++;
			}
		}
		return count;
	}

	private Logger root()
	{
		return context.getLogger(Logger.ROOT_LOGGER_NAME);
	}

	private void configure() throws JoranException
	{
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(context);
		configurator.doConfigure(new ByteArrayInputStream(CONFIG.getBytes(StandardCharsets.UTF_8)));
	}
}

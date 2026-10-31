package shortestpath;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;
import org.slf4j.LoggerFactory;

/**
 * Captures the log output of the named loggers for the duration of a test and keeps it off the
 * console. Tests that feed malformed input on purpose exercise the loaders' warn/error paths; with
 * this rule those expected lines are collected here (and can be asserted on) instead of showing up
 * as WARN/ERROR noise in the build output.
 *
 * A name may be a class's logger or a package prefix; events of child loggers reach the appender
 * because logback propagates them up the logger hierarchy.
 */
public final class LogCapture extends ExternalResource
{
	private final String[] loggerNames;
	private final List<Logger> loggers = new ArrayList<>();
	private final List<Boolean> additive = new ArrayList<>();
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	public LogCapture(String... loggerNames)
	{
		this.loggerNames = loggerNames;
	}

	@Override
	protected void before()
	{
		appender.start();
		for (String name : loggerNames)
		{
			Logger logger = (Logger) LoggerFactory.getLogger(name);
			loggers.add(logger);
			additive.add(logger.isAdditive());
			logger.addAppender(appender);
			logger.setAdditive(false);
		}
	}

	@Override
	protected void after()
	{
		for (int i = 0; i < loggers.size(); i++)
		{
			loggers.get(i).detachAppender(appender);
			loggers.get(i).setAdditive(additive.get(i));
		}
		appender.stop();
		loggers.clear();
		additive.clear();
	}

	/** Formatted messages captured so far, in order. */
	public List<String> messages()
	{
		return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
	}

	public void clear()
	{
		appender.list.clear();
	}
}

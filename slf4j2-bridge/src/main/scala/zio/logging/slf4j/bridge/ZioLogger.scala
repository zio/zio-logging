package zio.logging.slf4j.bridge

import org.slf4j.Marker
import org.slf4j.event.{ KeyValuePair, Level, LoggingEvent }
import org.slf4j.helpers.AbstractLogger
import org.slf4j.spi.LoggingEventAware

import java.util.Collections;

final class ZioLogger private[bridge] (name: String, factory: ZioLoggerFactory)
    extends AbstractLogger
    with LoggingEventAware {

  private val data: LoggerData = LoggerData(name)

  override def getName: String = name

  override protected def getFullyQualifiedCallerName: String = null

  override protected def handleNormalizedLoggingCall(
    level: Level,
    marker: Marker,
    messagePattern: String,
    arguments: Array[AnyRef],
    throwable: Throwable
  ): Unit =
    factory.log(data, level, messagePattern, arguments, throwable, Collections.emptyList[KeyValuePair]())

  override def isTraceEnabled: Boolean = factory.isEnabled(data, Level.TRACE)

  override def isTraceEnabled(marker: Marker): Boolean = isTraceEnabled

  override def isDebugEnabled: Boolean = factory.isEnabled(data, Level.DEBUG)

  override def isDebugEnabled(marker: Marker): Boolean = isDebugEnabled

  override def isInfoEnabled: Boolean = factory.isEnabled(data, Level.INFO)

  override def isInfoEnabled(marker: Marker): Boolean = isInfoEnabled

  override def isWarnEnabled: Boolean = factory.isEnabled(data, Level.WARN)

  override def isWarnEnabled(marker: Marker): Boolean = isWarnEnabled

  override def isErrorEnabled: Boolean = factory.isEnabled(data, Level.ERROR)

  override def isErrorEnabled(marker: Marker): Boolean = isErrorEnabled

  override def log(event: LoggingEvent): Unit =
    if (factory.isEnabled(data, event.getLevel))
      factory.log(
        data,
        event.getLevel,
        event.getMessage,
        event.getArgumentArray,
        event.getThrowable,
        event.getKeyValuePairs
      )
}

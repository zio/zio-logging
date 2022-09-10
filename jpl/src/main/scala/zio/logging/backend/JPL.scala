package zio.logging.backend

import zio.logging.LogFormat
import zio.logging.internal.LogAppender
import zio.{
  Cause,
  FiberFailure,
  FiberId,
  FiberRefs,
  LogLevel,
  LogSpan,
  Runtime,
  Trace,
  ZIO,
  ZIOAspect,
  ZLayer,
  ZLogger
}

object JPL {

  private val logLevelMapping: Map[LogLevel, System.Logger.Level] = Map(
    LogLevel.All     -> System.Logger.Level.ALL,
    LogLevel.Trace   -> System.Logger.Level.TRACE,
    LogLevel.Debug   -> System.Logger.Level.DEBUG,
    LogLevel.Info    -> System.Logger.Level.INFO,
    LogLevel.Warning -> System.Logger.Level.WARNING,
    LogLevel.Error   -> System.Logger.Level.ERROR,
    LogLevel.Fatal   -> System.Logger.Level.ERROR,
    LogLevel.None    -> System.Logger.Level.OFF
  )

  private val loggerNameKey = "jpl_logger_name"

  val logFormatDefault: LogFormat = LogFormat.allAnnotations + LogFormat.line + LogFormat.cause

  def loggerName(value: String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.logAnnotate(loggerNameKey, value)(zio)
    }

  private[backend] def getLoggerName(default: String = "zio-jpl-logger"): Trace => String =
    _ match {
      case Trace(location, _, _) =>
        val last = location.lastIndexOf(".")
        if (last > 0) {
          location.substring(0, last)
        } else location

      case _ => default
    }

  private def logAppender(systemLogger: System.Logger, logLevel: LogLevel): LogAppender = new LogAppender {
    self =>
    val message: StringBuilder = new StringBuilder()
    var throwable: Throwable   = null

    /**
     * cause as throwable
     */
    override def appendCause(cause: Cause[Any]): Unit = {
      if (!cause.isEmpty) {
        throwable = FiberFailure(cause)
      }
      ()
    }

    override def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)

    override def appendText(text: String): Unit = {
      message.append(text)
      ()
    }

    override def closeKeyOpenValue(): Unit =
      appendText("=")

    override def closeLogEntry(): Unit = {
      logLevelMapping.get(logLevel).foreach { level =>
        systemLogger.log(level, message.toString, throwable)
      }
      ()
    }

    override def closeValue(): Unit = appendText(" ")

    override def openKey(): Unit = ()

    override def openLogEntry(): Unit = {
      message.clear()

      throwable = null
      ()
    }
  }

  private def isLogLevelEnabled(systemLogger: System.Logger, logLevel: LogLevel): Boolean =
    logLevelMapping.get(logLevel).exists(systemLogger.isLoggable)

  def jpl(
    format: LogFormat,
    loggerName: Trace => String
  ): ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(jplLogger(format, loggerName))

  def jpl(
    format: LogFormat
  ): ZLayer[Any, Nothing, Unit] =
    jpl(format, getLoggerName())

  val jpl: ZLayer[Any, Nothing, Unit] =
    jpl(logFormatDefault)

  def jplLogger(
    format: LogFormat,
    loggerName: Trace => String = getLoggerName()
  ): ZLogger[String, Unit] =
    jplLogger(format, loggerName, System.getLogger)

  private[backend] def jplLogger(
    format: LogFormat,
    loggerName: Trace => String,
    getJPLogger: String => System.Logger
  ): ZLogger[String, Unit] =
    new ZLogger[String, Unit] {
      override def apply(
        trace: Trace,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => String,
        cause: Cause[Any],
        context: FiberRefs,
        spans: List[LogSpan],
        annotations: Map[String, String]
      ): Unit = {
        val jpLoggerName = annotations.getOrElse(loggerNameKey, loggerName(trace))
        val jpLogger     = getJPLogger(jpLoggerName)
        if (isLogLevelEnabled(jpLogger, logLevel)) {
          val appender = logAppender(jpLogger, logLevel)

          format.unsafeFormat(appender)(trace, fiberId, logLevel, message, cause, context, spans, annotations)
          appender.closeLogEntry()
        }
        ()
      }
    }

}

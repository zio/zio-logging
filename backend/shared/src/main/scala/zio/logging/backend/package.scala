package zio.logging

import zio.{ FiberId, LogLevel, LogSpan, RuntimeConfigAspect, ZFiberRef, ZLogger, ZTraceElement }

package object backend extends PlatformSpecificBackends {

  def consoleLogger(
    logLevel: zio.LogLevel,
    format: LogFormat[String]
  ): RuntimeConfigAspect =
    RuntimeConfigAspect.addLogger(simpleLogger(format, (_, line) => println(line), logLevel))

  def consoleErrLogger(
    logLevel: zio.LogLevel,
    format: LogFormat[String]
  ): RuntimeConfigAspect =
    RuntimeConfigAspect.addLogger(simpleLogger(format, (_, line) => Console.err.println(line), logLevel))

  val consoleLogger: RuntimeConfigAspect =
    consoleLogger(LogLevel.Info, LogFormat.default)

  def consoleLogger(level: LogLevel): RuntimeConfigAspect =
    consoleLogger(level, LogFormat.default)

  private[logging] def simpleLogger(
    format: LogFormat[String],
    writer: (LogLevel, String) => Unit,
    logLevel: zio.LogLevel
  ): ZLogger[Unit] =
    (
      trace: ZTraceElement,
      fiberId: FiberId,
      level: zio.LogLevel,
      message: () => String,
      context: Map[ZFiberRef.Runtime[_], AnyRef],
      spans: List[LogSpan]
    ) =>
      format
        .toLogger(LogFormatType.string)
        .filterLogLevel(_ >= logLevel)(trace, fiberId, level, message, context, spans)
        .foreach(writer(level, _))

}

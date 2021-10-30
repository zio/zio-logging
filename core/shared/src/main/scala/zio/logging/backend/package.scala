package zio.logging

import zio.{ FiberId, LogSpan, RuntimeConfigAspect, ZFiberRef, ZLogger, ZTraceElement }

package object backend {

  def console(
    logLevel: zio.LogLevel = zio.LogLevel.Info,
    format: ZLogger[String] = LogFormat.defaultFormat
  ): RuntimeConfigAspect =
    RuntimeConfigAspect(_.copy(logger = make(format, LogWriter.console, logLevel)))

  def make(
    format: ZLogger[String],
    writer: LogWriter,
    logLevel: zio.LogLevel
  ): ZLogger[Unit] =
    (
      trace: ZTraceElement,
      fiberId: FiberId,
      level: zio.LogLevel,
      message: () => String,
      context: Map[ZFiberRef.Runtime[_], AnyRef],
      spans: List[LogSpan]
    ) => format.filterLogLevel(_ >= logLevel)(trace, fiberId, level, message, context, spans).foreach(writer(_))

}

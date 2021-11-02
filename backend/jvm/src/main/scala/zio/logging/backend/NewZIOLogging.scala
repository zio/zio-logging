package zio.logging.backend

import zio.logging.LogFormat._
import zio.logging.{ LogFormat, annotate }
import zio.{ LogLevel, RuntimeConfigAspect, UIO, ZIO, ZIOAppDefault }

object NewZIOLogging extends ZIOAppDefault {

  val logFormat: LogFormat = logStash

  override def hook: RuntimeConfigAspect =
    zio.logging.backend.consoleLogger(LogLevel.Info, logFormat)

  override def run: UIO[Unit] =
    (ZIO.log("bla") *>
      ZIO.logError("""\  "omg""") *>
      ZIO.logDebug("foo")) @@ annotate("requestId" -> "111")
}

package zio.logging

import zio.logging.backend.LogFormat
import zio.{ RuntimeConfigAspect, UIO, ZIO, ZIOAppDefault }

object NewZIOLogging extends ZIOAppDefault {

  override def hook: RuntimeConfigAspect =
    zio.logging.backend.console(format = zio.logging.backend.LogFormat.coloredLogFormat)

  override def run: UIO[Unit] = ZIO.log("bla") *> ZIO.logError("omg") *> ZIO.logDebug("foo")
}

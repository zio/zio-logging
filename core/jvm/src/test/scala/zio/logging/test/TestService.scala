package zio.logging.test

import zio.{ UIO, ZIO }

object TestService {

  val testDebug: UIO[Unit] = ZIO.logDebug("test debug")

  val testInfo: UIO[Unit] = ZIO.logInfo("test info")

  val testWarning: UIO[Unit] = ZIO.logWarning("test warning")

  val testError: UIO[Unit] = ZIO.logError("test error")

}

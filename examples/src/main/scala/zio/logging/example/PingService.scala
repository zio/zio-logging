package zio.logging.example

import zio.{ Cause, Task, ULayer, ZIO, ZLayer }

import java.net.InetAddress

trait PingService {
  def ping(address: String): Task[Boolean]
}

object PingService {
  def ping(address: String): ZIO[PingService, Throwable, Boolean] = ZIO.serviceWithZIO[PingService](_.ping(address))
}

final class LivePingService extends PingService {
  override def ping(address: String): Task[Boolean] =
    for {
      inetAddress <-
        ZIO
          .attempt(InetAddress.getByName(address))
          .tapError(e => ZIO.logErrorCause(s"ping: $address - invalid address error", Cause.fail(e)))
      _           <- ZIO.logDebug(s"ping: $inetAddress")
      result      <- ZIO.attempt(inetAddress.isReachable(10000))
    } yield result
}

object LivePingService {
  val layer: ULayer[PingService] = ZLayer.succeed(new LivePingService)
}

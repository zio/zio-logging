package zio.logging.example

import zio._
import zio.Console.printLine
import zio.logging.backend.SLF4J
import zio.logging.consoleLogger
import zio.stream.ZStream

object TestApp extends ZIOAppDefault {
  val logFormat =
    "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause}"

  val configProvider: ConfigProvider = ConfigProvider.fromMap(
    Map(
      "logger/format"           -> logFormat,
      "logger/filter/rootLevel" -> "DEBUG"
    ),
    "/"
  )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> consoleLogger()

  def bad(i: Int): Task[Unit] =
    if (i == 5) {
      ZIO.fail(new Exception("Don't like 5's"))
    } else
      ZIO.log(s"Got $i")

  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    ZStream
      .fromChunks(Chunk(1 to 10: _*))
      .tap(bad)
      .runDrain
      .fork *> ZIO.never
}

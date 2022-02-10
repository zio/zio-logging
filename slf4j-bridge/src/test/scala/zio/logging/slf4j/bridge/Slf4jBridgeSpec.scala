package zio.logging.slf4j.bridge

import zio.test._
import zio.{
  Cause,
  Chunk,
  Exit,
  FiberId,
  LogLevel,
  LogSpan,
  RuntimeConfigAspect,
  ZFiberRef,
  ZIO,
  ZIOAppArgs,
  ZLogger,
  ZQueue,
  ZTraceElement
}

object Slf4jBridgeSpec extends DefaultRunnableSpec {

  final case class LogEntry(
    span: List[String],
    level: LogLevel,
    annotations: Map[String, String],
    entry: Exit[Any, String]
  )

  override def spec: Spec[TestEnvironment, TestFailure[Throwable], TestSuccess] =
    suite("Slf4jBridge")(
      test("logs through slf4j") {
        for {
          logQueue    <- ZQueue.unbounded[LogEntry]
          runtime     <- ZIO.runtime[Any]
          stringLogger = new ZLogger[String, Unit] {
                           override def apply(
                             trace: ZTraceElement,
                             fiberId: FiberId,
                             logLevel: LogLevel,
                             message: () => String,
                             context: Map[ZFiberRef.Runtime[_], AnyRef],
                             spans: List[LogSpan],
                             location: ZTraceElement,
                             annotations: Map[String, String]
                           ): Unit = {
                             val msg = message()
                             runtime.unsafeRun(
                               logQueue
                                 .offer(LogEntry(spans.map(_.label), logLevel, annotations, Exit.succeed(msg)))
                                 .unit
                             )
                           }
                         }
          causeLogger  = new ZLogger[Cause[Any], Unit] {
                           override def apply(
                             trace: ZTraceElement,
                             fiberId: FiberId,
                             logLevel: LogLevel,
                             message: () => Cause[Any],
                             context: Map[ZFiberRef.Runtime[_], AnyRef],
                             spans: List[LogSpan],
                             location: ZTraceElement,
                             annotations: Map[String, String]
                           ): Unit = {
                             val msg = message()
                             runtime.unsafeRun(
                               logQueue
                                 .offer(LogEntry(spans.map(_.label), logLevel, annotations, Exit.failCause(msg)))
                                 .unit
                             )
                           }
                         }
          rc          <- ZIO.runtimeConfig
          testFailure  = new RuntimeException("test error")
          _           <- (for {
                           _      <- Slf4jBridge.run.provideCustom(ZIO.succeed(ZIOAppArgs(Chunk.empty)).toLayer).ignore
                           logger <- ZIO.attempt(org.slf4j.LoggerFactory.getLogger("test.logger"))
                           _      <- ZIO.attempt(logger.debug("test debug message"))
                           _      <- ZIO.attempt(logger.warn("hello %s", "world"))
                           _      <- ZIO.attempt(logger.warn("warn cause", testFailure))
                           _      <- ZIO.attempt(logger.error("error", testFailure))
                         } yield ())
                           .withRuntimeConfig(
                             rc @@ RuntimeConfigAspect.addLogger(stringLogger) @@ RuntimeConfigAspect.addLogger(causeLogger)
                           )
                           .exit
          lines       <- logQueue.takeAll
        } yield assertTrue(
          lines == Chunk(
            LogEntry(
              List("test.logger"),
              LogLevel.Debug,
              Map.empty,
              Exit.succeed("test debug message")
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Warning,
              Map.empty,
              Exit.succeed("hello world")
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Warning,
              Map.empty,
              Exit.die(WrappedException("warn cause", testFailure))
            ),
            LogEntry(
              List("test.logger"),
              LogLevel.Error,
              Map.empty,
              Exit.die(WrappedException("error", testFailure))
            )
          )
        )
      }
    )
}

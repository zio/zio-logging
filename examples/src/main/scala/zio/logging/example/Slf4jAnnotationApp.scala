package zio.logging.example

import zio.logging.backend.SLF4J
import zio.logging.{ LogAnnotation, LogFormat }
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

object Slf4jAnnotationApp extends ZIOAppDefault {

  private val logger =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(
      LogLevel.Info,
      LogFormat.annotation(LogAnnotation.TraceId) |-| LogFormat.annotation(
        "user"
      ) |-| LogFormat.line |-| LogFormat.cause
    )

  private val users = List.fill(2)(UUID.randomUUID())

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.foreachPar(users) { uId =>
                   {
                     ZIO.logInfo("Starting operation") *>
                       ZIO.sleep(500.millis) *>
                       ZIO.logInfo("Stopping operation")
                   } @@ ZIOAspect.annotated("user", uId.toString)
                 } @@ LogAnnotation.TraceId(traceId)
      _       <- ZIO.logInfo("Done")
    } yield ExitCode.success).provide(logger)

}

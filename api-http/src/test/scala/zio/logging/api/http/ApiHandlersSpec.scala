package zio.logging.api.http

import zio.{ ZIO, ZLayer }
import zio.http._
import zio.http.codec._
import zio.LogLevel
import zio.test._

object ApiHandlersSpec extends ZIOSpecDefault {

  val loggerService = ZLayer.succeed {
    new LoggerService {
      override def getLoggerConfigurations(): ZIO[Any, Throwable, List[Domain.LoggerConfiguration]] =
        ZIO.succeed(Domain.LoggerConfiguration("root", LogLevel.Info) :: Nil)

      override def getLoggerConfiguration(name: String): ZIO[Any, Throwable, Option[Domain.LoggerConfiguration]] =
        ZIO.succeed(Some(Domain.LoggerConfiguration(name, LogLevel.Info)))

      override def setLoggerConfiguration(
        name: String,
        logLevel: LogLevel
      ): ZIO[Any, Throwable, Domain.LoggerConfiguration] =
        ZIO.succeed(Domain.LoggerConfiguration(name, logLevel))
    }
  }

  def spec = suite("ApiHandlersSpec")(
    test("get all") {

      val routes = ApiHandlers.routes("example" :: Nil)

      val request = Request.get(URL.decode("/example/logger").toOption.get)

      for {
        response <- routes.toApp.runZIO(request)
        content  <- HttpCodec.content[List[Domain.LoggerConfiguration]].decodeResponse(response)
      } yield assertTrue(response.status.isSuccess) && assertTrue(
        content == List(Domain.LoggerConfiguration("root", LogLevel.Info))
      )
    }.provideLayer(loggerService),
    test("get") {
      val routes  = ApiHandlers.routes("example" :: Nil)
      val request = Request.get(URL.decode("/example/logger/example.Service").toOption.get)

      for {
        response <- routes.toApp.runZIO(request)
        content  <- HttpCodec.content[Domain.LoggerConfiguration].decodeResponse(response)
      } yield assertTrue(response.status.isSuccess) && assertTrue(
        content == Domain.LoggerConfiguration("example.Service", LogLevel.Info)
      )
    }.provideLayer(loggerService),
    test("set") {

      import Domain.logLevelSchema

      val routes = ApiHandlers.routes("example" :: Nil)

      for {
        request  <- ZIO.attempt(
                      Request
                        .put(
                          HttpCodec.content[LogLevel].encodeRequest(LogLevel.Warning).body,
                          URL.decode("/example/logger/example.Service").toOption.get
                        )
                    )
        response <- routes.toApp.runZIO(request)
        content  <- HttpCodec.content[Domain.LoggerConfiguration].decodeResponse(response)
      } yield assertTrue(response.status.isSuccess) && assertTrue(
        content == Domain.LoggerConfiguration("example.Service", LogLevel.Warning)
      )
    }.provideLayer(loggerService)
  )

}

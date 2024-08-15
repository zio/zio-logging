package zio.logging.api.http

import zio.http._
import zio.http.codec._
import zio.logging.LoggerConfigurer
import zio.test._
import zio.{ LogLevel, ULayer, ZIO, ZLayer }

object ApiHandlersSpec extends ZIOSpecDefault {

  val loggerConfigurer: ULayer[LoggerConfigurer] = ZLayer.succeed {
    new LoggerConfigurer {
      override def getLoggerConfigs(): ZIO[Any, Throwable, List[LoggerConfigurer.LoggerConfig]] =
        ZIO.succeed(LoggerConfigurer.LoggerConfig("root", LogLevel.Info) :: Nil)

      override def getLoggerConfig(
        name: String
      ): ZIO[Any, Throwable, Option[LoggerConfigurer.LoggerConfig]] =
        ZIO.succeed(Some(LoggerConfigurer.LoggerConfig(name, LogLevel.Info)))

      override def setLoggerConfig(
        name: String,
        level: LogLevel
      ): ZIO[Any, Throwable, LoggerConfigurer.LoggerConfig] =
        ZIO.succeed(LoggerConfigurer.LoggerConfig(name, level))
    }
  }

  def spec: Spec[TestEnvironment, Any] = suite("ApiHandlersSpec")(
    test("get all") {
      val routes = ApiHandlers.routes("example" :: Nil)

      for {
        request  <- ZIO.attempt(Request.get(URL.decode("/example/logger").toOption.get))
        response <- routes.runZIO(request)
        content  <- HttpCodec.content[List[ApiDomain.LoggerConfig]].decodeResponse(response)
      } yield assertTrue(response.status.isSuccess) && assertTrue(
        content == List(ApiDomain.LoggerConfig("root", LogLevel.Info))
      )
    },
    test("get") {
      val routes = ApiHandlers.routes("example" :: Nil)
      for {
        request  <- ZIO.attempt(Request.get(URL.decode("/example/logger/example.Service").toOption.get))
        response <- routes.runZIO(request)
        content  <- HttpCodec.content[ApiDomain.LoggerConfig].decodeResponse(response)
      } yield assertTrue(response.status.isSuccess) && assertTrue(
        content == ApiDomain.LoggerConfig("example.Service", LogLevel.Info)
      )
    },
    test("set") {
      import ApiDomain.logLevelSchema
      val routes = ApiHandlers.routes("example" :: Nil)
      for {
        request  <- ZIO.attempt(
                      Request
                        .put(
                          URL.decode("/example/logger/example.Service").toOption.get,
                          HttpCodec.content[LogLevel].encodeRequest(LogLevel.Warning).body
                        )
                    )
        response <- routes.runZIO(request)
        content  <- HttpCodec.content[ApiDomain.LoggerConfig].decodeResponse(response)
      } yield assertTrue(response.status.isSuccess) && assertTrue(
        content == ApiDomain.LoggerConfig("example.Service", LogLevel.Warning)
      )
    }
  ).provideLayer(loggerConfigurer)

}

package zio.logging.api.http

import zio.LogLevel
import zio.http.codec._
import zio.http.codec.PathCodec.literal
import zio.test._

object ApiEndpointsSpec extends ZIOSpecDefault {

  def spec = suite("ApiEndpointsSpec")(
    test("rootPathCodec") {
      def testRootPathCodec(rootPath: Iterable[String], expected: PathCodec[Unit]) =
        assertTrue(ApiEndpoints.rootPathCodec(rootPath).encodeRequest(()).url == expected.encodeRequest(()).url)

      testRootPathCodec(Nil, HttpCodec.empty) && testRootPathCodec(
        "example" :: Nil,
        literal("example")
      ) && testRootPathCodec("v1" :: "example" :: Nil, literal("v1") / literal("example"))
    },
    test("getLoggerConfigurations") {

      def testPath(rootPath: Iterable[String], expected: PathCodec[Unit]) =
        assertTrue(
          ApiEndpoints.getLoggerConfigurations(rootPath).input.encodeRequest(()).url == expected.encodeRequest(()).url
        )

      testPath(Nil, literal("logger")) && testPath(
        "example" :: Nil,
        literal("example") / literal("logger")
      )
    },
    test("getLoggerConfiguration") {

      def testPath(rootPath: Iterable[String], expected: PathCodec[Unit]) =
        assertTrue(
          ApiEndpoints.getLoggerConfiguration(rootPath).input.encodeRequest("my-logger").url == expected
            .encodeRequest(())
            .url
        )

      testPath(Nil, literal("logger") / literal("my-logger")) && testPath(
        "example" :: Nil,
        literal("example") / literal("logger") / literal("my-logger")
      )
    },
    test("setLoggerConfigurations") {

      def testPath(rootPath: Iterable[String], expected: PathCodec[Unit]) =
        assertTrue(
          ApiEndpoints
            .setLoggerConfiguration(rootPath)
            .input
            .encodeRequest(("my-logger", LogLevel.Info))
            .url == expected
            .encodeRequest(())
            .url
        )

      testPath(Nil, literal("logger") / literal("my-logger")) && testPath(
        "example" :: Nil,
        literal("example") / literal("logger") / literal("my-logger")
      )
    }
  )

}

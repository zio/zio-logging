package zio.logging.api.http

import zio.http.codec.PathCodec.literal
import zio.http.codec._
import zio.test._
import zio.{ LogLevel, Scope }

object ApiEndpointsSpec extends ZIOSpecDefault {

  def spec: Spec[Environment with TestEnvironment with Scope, Any] = suite("ApiEndpointsSpec")(
    test("rootPathCodec") {
      def testRootPathCodec(rootPath: Seq[String], expected: PathCodec[Unit]) =
        assertTrue(ApiEndpoints.rootPathCodec(rootPath).encodeRequest(()).url == expected.encodeRequest(()).url)

      testRootPathCodec(Nil, HttpCodec.empty) && testRootPathCodec(
        "example" :: Nil,
        literal("example")
      ) && testRootPathCodec("v1" :: "example" :: Nil, literal("v1") / literal("example"))
    },
    test("getLoggerConfigs") {

      def testPath(rootPath: Seq[String], expected: PathCodec[Unit]) =
        assertTrue(
          ApiEndpoints.getLoggerConfigs(rootPath).input.encodeRequest(()).url == expected.encodeRequest(()).url
        )

      testPath(Nil, literal("logger")) && testPath(
        "example" :: Nil,
        literal("example") / literal("logger")
      )
    },
    test("getLoggerConfig") {

      def testPath(rootPath: Seq[String], expected: PathCodec[Unit]) =
        assertTrue(
          ApiEndpoints.getLoggerConfig(rootPath).input.encodeRequest("my-logger").url == expected
            .encodeRequest(())
            .url
        )

      testPath(Nil, literal("logger") / literal("my-logger")) && testPath(
        "example" :: Nil,
        literal("example") / literal("logger") / literal("my-logger")
      )
    },
    test("setLoggerConfigs") {

      def testPath(rootPath: Seq[String], expected: PathCodec[Unit]) =
        assertTrue(
          ApiEndpoints
            .setLoggerConfig(rootPath)
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

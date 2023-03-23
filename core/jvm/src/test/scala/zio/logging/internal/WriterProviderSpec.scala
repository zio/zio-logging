package zio.logging.internal

import zio.test._

import java.nio.file.FileSystems
import java.time.LocalDate

object WriterProviderSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("WriterProvider")(
    test("Make path include date") {
      import WriterProvider.TimeBasedRollingWriterProvider.makePath

      val localDateTime = LocalDate.of(2023, 3, 21).atStartOfDay()
      val destination1   = FileSystems.getDefault.getPath("/tmp/file_app")
      val destination2   = FileSystems.getDefault.getPath("/tmp/file_app.log")
      val destination3   = FileSystems.getDefault.getPath("/tmp/file.app.log")
      val destination4   = FileSystems.getDefault.getPath("/tmp/file.app.out.log")

      assertTrue(
        (makePath(destination1, localDateTime).toString == "/tmp/file_app-2023-03-21") &&
          (makePath(destination2, localDateTime).toString == "/tmp/file_app-2023-03-21.log") &&
          (makePath(destination3, localDateTime).toString == "/tmp/file.app-2023-03-21.log") &&
          (makePath(destination4, localDateTime).toString == "/tmp/file.app.out-2023-03-21.log")
      )
    }
  )
}

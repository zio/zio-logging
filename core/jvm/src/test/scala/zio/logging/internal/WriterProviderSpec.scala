package zio.logging.internal

import zio.test._

import java.nio.file.FileSystems
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object WriterProviderSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("WriterProvider")(
    test("Make path include date") {
      val destination = FileSystems.getDefault.getPath("/tmp/file_app.log")
      val localDateTime = LocalDate.of(2023, 3, 21).atStartOfDay()
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

      assertTrue(WriterProvider.TimeBasedRollingWriterProvider.makePath(destination, localDateTime, formatter).toString == "/tmp/file_app-2023-03-21.log")
    }
  )
}

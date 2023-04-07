package zio.logging.internal

import zio.ZIO
import zio.test._

import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.time.{ LocalDate, LocalDateTime }
import java.util.concurrent.atomic.AtomicReference

object WriterProviderSpec extends ZIOSpecDefault {

  val spec: Spec[Environment, Any] = suite("WriterProvider")(
    test("Make path include date") {
      import WriterProvider.TimeBasedRollingWriterProvider.makePath

      val localDateTime = LocalDate.of(2023, 3, 21).atStartOfDay()
      val destination1  = FileSystems.getDefault.getPath("/tmp/file_app")
      val destination2  = FileSystems.getDefault.getPath("/tmp/file_app.log")
      val destination3  = FileSystems.getDefault.getPath("/tmp/file.app.log")
      val destination4  = FileSystems.getDefault.getPath("/tmp/file.app.out.log")

      assertTrue(
        (makePath(destination1, localDateTime).toString == "/tmp/file_app-2023-03-21") &&
          (makePath(destination2, localDateTime).toString == "/tmp/file_app-2023-03-21.log") &&
          (makePath(destination3, localDateTime).toString == "/tmp/file.app-2023-03-21.log") &&
          (makePath(destination4, localDateTime).toString == "/tmp/file.app.out-2023-03-21.log")
      )
    },
    test("Called multiple times with same time, if it return same writer") {
      val timeRef = new AtomicReference[LocalDateTime](LocalDateTime.now())

      val testMakeNewTime: () => LocalDateTime = () => timeRef.get()

      val writerProvider = WriterProvider.TimeBasedRollingWriterProvider(
        destination = FileSystems.getDefault.getPath("/tmp/file_app"),
        charset = StandardCharsets.UTF_8,
        bufferedIOSize = Some(1),
        time = testMakeNewTime
      )

      val parallelExecution = ZIO
        .foreachPar(1 to 5)(_ => ZIO.succeed(writerProvider.writer))

      for {
        sameWriter1 <- parallelExecution.map(_.toSet)
        _            = timeRef.set(LocalDateTime.now().plusDays(1))
        sameWriter2 <- parallelExecution.map(_.toSet)
      } yield assertTrue(sameWriter1.size == 1 && sameWriter2.size == 1 && sameWriter1 != sameWriter2)
    }
  )
}

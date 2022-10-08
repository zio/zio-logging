package zio.logging

import org.openjdk.jmh.annotations._
import zio.{ LogLevel, Runtime, Unsafe, ZIO, ZIOAspect, ZLayer }

import java.util.concurrent.TimeUnit
import scala.util.Random

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class FilterBenchmarks {
  val loggerNameAndLevel: LogGroup[(String, LogLevel)] =
    (_, logLevel, _, annotations) => annotations.getOrElse("name", "") -> logLevel

  val runtime = Runtime.default

  val unfilteredLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> console(LogFormat.default, LogFilter.acceptAll)

  val handWrittenFilteredLogging: ZLayer[Any, Nothing, Unit] = {
    val filter: LogFilter = (trace, _, level, _, _, context, _, annotations) => {
      val loggerNames = loggerNameAndLevel(trace, level, context, annotations)._1.split(".").toList
      loggerNames match {
        case List("a", "b", "c") => level >= LogLevel.Info
        case List("a", "b", "d") => level >= LogLevel.Warning
        case List("e")           => level >= LogLevel.Info
        case _                   => level >= LogLevel.Debug
      }
    }
    Runtime.removeDefaultLoggers >>> console(LogFormat.default, filter)
  }

  val filterByLogLevelAndNameLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> console(
      LogFormat.default,
      LogFilter.logLevelByGroup(
        LogLevel.Debug,
        loggerNameAndLevel,
        "a.b.c" -> LogLevel.Info,
        "a.b.d" -> LogLevel.Warning,
        "e"     -> LogLevel.Info
      )
    )

  val cachedFilterByLogLevelAndNameLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> console(
      LogFormat.default,
      LogFilter
        .logLevelByGroup(
          LogLevel.Debug,
          loggerNameAndLevel,
          "a.b.c" -> LogLevel.Info,
          "a.b.d" -> LogLevel.Warning,
          "e"     -> LogLevel.Info
        )
        .cacheWith(loggerNameAndLevel)
    )

  val names: List[String] = List(
    "a",
    "a.b",
    "a.b.c",
    "a.b.d",
    "a.b.e",
    "a.b.e.f.g",
    "a.z",
    "e",
    "e.f",
    "e.f",
    "a.e.f"
  )

  val rnd = new Random(12345)

  def testLoggingWith(logging: ZLayer[Any, Nothing, Unit]): Unit = {
    val name = names(rnd.nextInt(names.length))
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run {
        (ZIO.logDebug("test") @@ ZIOAspect.annotated("name", name)).provide(logging)
      }
    }
    ()
  }

  /**
   * 2022/09/17 Initial results
   *
   * jmh:run -i 3 -wi 3 -f1 -t1 .*FilterBenchmarks.*
   *
   * Benchmark                                           Mode  Cnt      Score       Error  Units
   * FilterBenchmarks.cachedFilterByLogLevelAndNameLog  thrpt    3  14073.892 ±  1905.997  ops/s
   * FilterBenchmarks.filterByLogLevelAndNameLog        thrpt    3  11243.144 ± 23282.211  ops/s
   * FilterBenchmarks.handWrittenFilterLog              thrpt    3  11782.559 ±  2342.516  ops/s
   * FilterBenchmarks.noFilteringLog                    thrpt    3  11853.834 ± 12589.637  ops/s
   */

  @Benchmark
  def noFilteringLog(): Unit =
    testLoggingWith(unfilteredLogging)

  @Benchmark
  def handWrittenFilterLog(): Unit =
    testLoggingWith(handWrittenFilteredLogging)

  @Benchmark
  def filterByLogLevelAndNameLog(): Unit =
    testLoggingWith(filterByLogLevelAndNameLogging)

  @Benchmark
  def cachedFilterByLogLevelAndNameLog(): Unit =
    testLoggingWith(cachedFilterByLogLevelAndNameLogging)

}

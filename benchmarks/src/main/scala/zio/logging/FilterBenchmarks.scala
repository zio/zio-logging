package zio.logging

import org.openjdk.jmh.annotations._
import zio.{ LogLevel, Runtime, Unsafe, ZIO, ZIOAspect, ZLayer }

import java.util.concurrent.TimeUnit
import scala.util.Random

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class FilterBenchmarks {
  val loggerName: LogGroup[String] =
    (_, _, _, annotations) => annotations.getOrElse("name", "")

  val loggerNameAndLevel: LogGroup[(String, LogLevel)] =
    (_, logLevel, _, annotations) => annotations.getOrElse("name", "") -> logLevel

  val runtime = Runtime.default

  val unfilteredLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> console(LogFormat.default, LogFilter.acceptAll)

  val handWrittenFilteredLogging: ZLayer[Any, Nothing, Unit] = {
    val filter: LogFilter[String] = (trace, _, level, _, _, context, _, annotations) => {
      val loggerNames = LogFilter.splitNameByDot(loggerName(trace, level, context, annotations))
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
        loggerName,
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
          loggerName,
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
   * 2022/10/12 Initial results
   *
   * jmh:run -i 3 -wi 3 -f1 -t1 .*FilterBenchmarks.*
   *
   * Benchmark                                           Mode  Cnt      Score       Error  Units
   * FilterBenchmarks.cachedFilterByLogLevelAndNameLog  thrpt    3  14759.632 ±  2550.790  ops/s
   * FilterBenchmarks.filterByLogLevelAndNameLog        thrpt    3  15068.682 ±   743.259  ops/s
   * FilterBenchmarks.handWrittenFilterLog              thrpt    3  14047.130 ±  1358.382  ops/s
   * FilterBenchmarks.noFilteringLog                    thrpt    3  10924.866 ± 18515.917  ops/s
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

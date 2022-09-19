package zio.logging

import org.openjdk.jmh.annotations._
import zio.stm.TMap
import zio.{ FiberRefs, LogLevel, Runtime, Trace, Unsafe, ZIO, ZIOAspect, ZLayer }

import java.util.concurrent.TimeUnit
import scala.util.Random

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class FilterBenchmarks {

  val loggerName: (Trace, FiberRefs, Map[String, String]) => String = (_, _, annotations) =>
    annotations.getOrElse("name", "")

  val runtime = Runtime.default

  val unfilteredLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> console(LogFormat.default, LogFilter.acceptAll)

  val handWrittenFilteredLogging: ZLayer[Any, Nothing, Unit] = {
    val filter: LogFilter = (trace, level, context, annotations) => {
      val loggerNames = loggerName(trace, context, annotations).split(".").toList
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
      LogFilter.logLevelAndName(
        LogLevel.Debug,
        loggerName,
        "a.b.c" -> LogLevel.Info,
        "a.b.d" -> LogLevel.Warning,
        "e"     -> LogLevel.Info
      )
    )

  val tmapCachedFilterByLogLevelAndNameLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> ZLayer.fromZIO {
      TMap.empty[(List[String], LogLevel), Boolean].commit.map { cache =>
        LogFilter.cachedLogLevelAndName(
          cache,
          LogLevel.Debug,
          loggerName,
          "a.b.c" -> LogLevel.Info,
          "a.b.d" -> LogLevel.Warning,
          "e"     -> LogLevel.Info
        )
      }
    }.flatMap { env =>
      console(LogFormat.default, env.get[LogFilter])
    }

  val cachedFilterByLogLevelAndNameLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> console(
      LogFormat.default,
      LogFilter.cachedLogLevelAndName(
        LogLevel.Debug,
        loggerName,
        "a.b.c" -> LogLevel.Info,
        "a.b.d" -> LogLevel.Warning,
        "e"     -> LogLevel.Info
      )
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
   * Benchmark                                               Mode  Cnt      Score      Error  Units
   * FilterBenchmarks.cachedFilterByLogLevelAndNameLog      thrpt    3  14830.705 ± 2042.084  ops/s
   * FilterBenchmarks.filterByLogLevelAndNameLog            thrpt    3  14794.678 ± 1603.926  ops/s
   * FilterBenchmarks.handWrittenFilterLog                  thrpt    3  13041.006 ± 1225.018  ops/s
   * FilterBenchmarks.noFilteringLog                        thrpt    3  13074.786 ±  512.533  ops/s
   * FilterBenchmarks.tmapCachedFilterByLogLevelAndNameLog  thrpt    3   9875.576 ± 5103.356  ops/s
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
  def tmapCachedFilterByLogLevelAndNameLog(): Unit =
    testLoggingWith(tmapCachedFilterByLogLevelAndNameLogging)

  @Benchmark
  def cachedFilterByLogLevelAndNameLog(): Unit =
    testLoggingWith(cachedFilterByLogLevelAndNameLogging)

}

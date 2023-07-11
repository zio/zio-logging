package zio.logging

import org.openjdk.jmh.annotations._
import zio.{ LogLevel, Runtime, Unsafe, ZIO, ZLayer }

import java.util.concurrent.TimeUnit
import scala.util.Random

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class FilterBenchmarks {

  val runtime = Runtime.default

  val unfilteredLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> makeSystemOutLogger(LogFormat.default.toLogger).install

  val handWrittenFilteredLogging: ZLayer[Any, Nothing, Unit] = {
    val loggerNameGroup: LogGroup[Any, String] = LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogGroup()
    val filter: LogFilter[String]              = LogFilter[String, (List[String], LogLevel)](
      LogGroup { (trace, fiberId, level, message, cause, context, spans, annotations) =>
        val loggerNames =
          LogFilter.splitNameByDot(loggerNameGroup(trace, fiberId, level, message, cause, context, spans, annotations))

        loggerNames -> level
      },
      v => {
        val (loggerNames, level) = v
        loggerNames match {
          case List("a", "b", "c") => level >= LogLevel.Info
          case List("a", "b", "d") => level >= LogLevel.Warning
          case List("e")           => level >= LogLevel.Info
          case List("f", "g")      => level >= LogLevel.Error
          case List("f")           => level >= LogLevel.Info
          case _                   => level >= LogLevel.Debug
        }
      }
    )
    Runtime.removeDefaultLoggers >>> makeSystemOutLogger(LogFormat.default.toLogger)
      .map(logger => FilteredLogger(logger, filter))
      .install
  }

  val filterByLogLevelAndNameLogging: ZLayer[Any, Nothing, Unit] = {
    val filter = LogFilter
      .LogLevelByNameConfig(
        LogLevel.Debug,
        "a.b.c" -> LogLevel.Info,
        "a.b.d" -> LogLevel.Warning,
        "e"     -> LogLevel.Info,
        "f.g"   -> LogLevel.Error,
        "f"     -> LogLevel.Info
      )
      .toFilter

    Runtime.removeDefaultLoggers >>> makeSystemOutLogger(LogFormat.default.toLogger)
      .map(logger => FilteredLogger(logger, filter))
      .install
  }

  val cachedFilterByLogLevelAndNameLogging: ZLayer[Any, Nothing, Unit] = {
    val filter = LogFilter
      .LogLevelByNameConfig(
        LogLevel.Debug,
        "a.b.c" -> LogLevel.Info,
        "a.b.d" -> LogLevel.Warning,
        "e"     -> LogLevel.Info,
        "f.g"   -> LogLevel.Error,
        "f"     -> LogLevel.Info
      )
      .toFilter
      .cached

    Runtime.removeDefaultLoggers >>> makeSystemOutLogger(LogFormat.default.toLogger)
      .map(logger => FilteredLogger(logger, filter))
      .install
  }

  val names: List[String] = List(
    "a",
    "a.b",
    "a.b.c",
    "a.b.d",
    "a.b.e",
    "a.b.e.f.g",
    "a.z",
    "b.c",
    "b.d",
    "b.e",
    "e",
    "e.f",
    "e.f",
    "a.e.f",
    "f.g",
    "f.g.h",
    "f.g.h.x",
    "f.h",
    "f.h.x",
    "f"
  )

  val rnd = new Random(12345)

  def testLoggingWith(logging: ZLayer[Any, Nothing, Unit]): Unit = {
    val name = names(rnd.nextInt(names.length))
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run {
        (ZIO.logDebug("test") @@ loggerName(name)).provide(logging)
      }
    }
    ()
  }

  /**
   * 2022/10/28 Initial results
   *
   * jmh:run -i 3 -wi 3 -f1 -t1 .*FilterBenchmarks.*
   *
   * Benchmark                                           Mode  Cnt      Score       Error  Units
   * FilterBenchmarks.cachedFilterByLogLevelAndNameLog  thrpt    3  16623.054 ± 15855.331  ops/s
   * FilterBenchmarks.filterByLogLevelAndNameLog        thrpt    3  18048.598 ±  3868.976  ops/s
   * FilterBenchmarks.handWrittenFilterLog              thrpt    3  16352.488 ±  2316.372  ops/s
   * FilterBenchmarks.noFilteringLog                    thrpt    3  15104.002 ±  3857.108  ops/s
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

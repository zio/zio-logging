package zio.logging

import org.openjdk.jmh.annotations._
import zio.{ LogLevel, Runtime, Unsafe, ZIO, ZIOAspect, ZLayer }

import java.util.concurrent.TimeUnit
import scala.util.Random

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class FilterBenchmarks {
  val loggerName: LogGroup[Any, String] = LoggerNameExtractor.annotation("name").toLogGroup()

  val loggerNameAndLevel: LogGroup[Any, (String, LogLevel)] = loggerName ++ LogGroup.logLevel

  val runtime = Runtime.default

  val unfilteredLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> console(LogFormat.default, LogFilter.acceptAll)

  val handWrittenFilteredLogging: ZLayer[Any, Nothing, Unit] = {
    val filter: LogFilter[String] = LogFilter[String, (List[String], LogLevel)](
      LogGroup { (trace, fiberId, level, message, cause, context, spans, annotations) =>
        val loggerNames =
          LogFilter.splitNameByDot(loggerName(trace, fiberId, level, message, cause, context, spans, annotations))

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
        "e"     -> LogLevel.Info,
        "f.g"   -> LogLevel.Error,
        "f"     -> LogLevel.Info
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
          "e"     -> LogLevel.Info,
          "f.g"   -> LogLevel.Error,
          "f"     -> LogLevel.Info
        )
        .cached
    )

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
        (ZIO.logDebug("test") @@ ZIOAspect.annotated("name", name)).provide(logging)
      }
    }
    ()
  }

  /**
   * 2022/10/16 Initial results
   *
   * jmh:run -i 3 -wi 3 -f1 -t1 .*FilterBenchmarks.*
   *
   * Benchmark                                           Mode  Cnt      Score      Error  Units
   * FilterBenchmarks.cachedFilterByLogLevelAndNameLog  thrpt    3  15281.411 ± 2505.438  ops/s
   * FilterBenchmarks.filterByLogLevelAndNameLog        thrpt    3  14997.525 ± 2905.031  ops/s
   * FilterBenchmarks.handWrittenFilterLog              thrpt    3  12653.665 ± 9558.955  ops/s
   * FilterBenchmarks.noFilteringLog                    thrpt    3  11856.215 ± 3469.573  ops/s
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

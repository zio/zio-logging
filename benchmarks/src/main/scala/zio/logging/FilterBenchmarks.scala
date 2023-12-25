package zio.logging

import org.openjdk.jmh.annotations._
import zio.{ ConfigProvider, LogLevel, Runtime, Unsafe, ZIO, ZLayer }

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
      .filter(filter)
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
      .filter(filter)
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
      .filter(filter)
      .install
  }

  val reconfigurableFilterByLogLevelAndNameLogging: ZLayer[Any, Nothing, Unit] = {
    val logFormat =
      "%label{timestamp}{%fixed{32}{%timestamp}} %label{level}{%level} %label{thread}{%fiberId} %label{message}{%message} %label{cause}{%cause}"

    val configProvider: ConfigProvider = ConfigProvider.fromMap(
      Map(
        "logger/format"                -> logFormat,
        "logger/filter/rootLevel"      -> LogLevel.Debug.label,
        "logger/filter/mappings/a.b.c" -> LogLevel.Info.label,
        "logger/filter/mappings/a.b.d" -> LogLevel.Warning.label,
        "logger/filter/mappings/e"     -> LogLevel.Info.label,
        "logger/filter/mappings/f.g"   -> LogLevel.Error.label,
        "logger/filter/mappings/f"     -> LogLevel.Info.label
      ),
      "/"
    )

    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> ReconfigurableLogger
      .make[Any, Nothing, String, Any, ConsoleLoggerConfig](
        ConsoleLoggerConfig.load().orDie,
        (config, _) => makeConsoleLogger(config)
      )
      .installUnscoped[Any]
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
   * 2023/12/25 Initial results
   *
   * jmh:run -i 3 -wi 3 -f1 -t1 .*FilterBenchmarks.*
   *
   * Benchmark                                                   Mode  Cnt      Score       Error  Units
   * FilterBenchmarks.cachedFilterByLogLevelAndNameLog          thrpt    3  15098.312 ±  4204.210  ops/s
   * FilterBenchmarks.filterByLogLevelAndNameLog                thrpt    3  13100.786 ±  2017.585  ops/s
   * FilterBenchmarks.handWrittenFilterLog                      thrpt    3  10864.716 ±   482.042  ops/s
   * FilterBenchmarks.noFilteringLog                            thrpt    3   8813.185 ± 10371.239  ops/s
   * FilterBenchmarks.reconfigurableFilterByLogLevelAndNameLog  thrpt    3   3334.433 ±   216.060  ops/s
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

  @Benchmark
  def reconfigurableFilterByLogLevelAndNameLog(): Unit =
    testLoggingWith(reconfigurableFilterByLogLevelAndNameLogging)

}

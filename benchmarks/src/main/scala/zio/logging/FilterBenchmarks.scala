package zio.logging

import org.openjdk.jmh.annotations._
import zio.logging.LogFiltering.{ cachedFilterBy, filterBy }
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
    Runtime.removeDefaultLoggers >>> console(LogFormat.default, LogFiltering.default)

  val handWrittenFilteredLogging: ZLayer[Any, Nothing, Unit] = {
    val filter: LogFiltering.Filter = (trace, level, context, annotations) => {
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

  val filterTreeLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> console(
      LogFormat.default,
      filterBy(
        LogLevel.Debug,
        loggerName,
        "a.b.c" -> LogLevel.Info,
        "a.b.d" -> LogLevel.Warning,
        "e"     -> LogLevel.Info
      )
    )

  val cachedFilterLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> ZLayer.fromZIO {
      TMap.empty[(List[String], LogLevel), Boolean].commit.map { cache =>
        cachedFilterBy(
          cache,
          LogLevel.Debug,
          loggerName,
          "a.b.c" -> LogLevel.Info,
          "a.b.d" -> LogLevel.Warning,
          "e"     -> LogLevel.Info
        )
      }
    }.flatMap { env =>
      console(LogFormat.default, env.get[LogFiltering.Filter])
    }

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
  }

  /**
   * 2022/09/17 Initial results
   *
   * jmh:run -i 3 -wi 3 -f1 -t1 .*FilterBenchmarks.*
   *
   * Benchmark                                   Mode  Cnt      Score       Error  Units
   * FilterBenchmarks.cachedFilterTreeLog       thrpt    3   9032.190 ±  3481.996  ops/s
   * FilterBenchmarks.filterTreeLog             thrpt    3  13544.625 ± 12022.233  ops/s
   * FilterBenchmarks.handWrittenFilterLogging  thrpt    3  12316.480 ±  4649.164  ops/s
   * FilterBenchmarks.noFilteringLogging        thrpt    3  12690.704 ±   367.390  ops/s
   */

  @Benchmark
  def noFilteringLogging(): Unit =
    testLoggingWith(unfilteredLogging)

  @Benchmark
  def handWrittenFilterLogging(): Unit =
    testLoggingWith(handWrittenFilteredLogging)

  @Benchmark
  def filterTreeLog(): Unit =
    testLoggingWith(filterTreeLogging)

  @Benchmark
  def cachedFilterTreeLog(): Unit =
    testLoggingWith(cachedFilterLogging)

}

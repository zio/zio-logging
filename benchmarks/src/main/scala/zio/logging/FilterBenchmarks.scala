package zio.logging

import org.openjdk.jmh.annotations._
import zio.ZLayer
import zio.logging.LogFiltering.{cachedFilterBy, filterBy}
import zio.stm.TMap

import java.util.concurrent.TimeUnit
import scala.util.Random

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class FilterBenchmarks {

  val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  val unfilteredLogging: Logging                                          = runtime.unsafeRun {
    (LogAppender.ignore[String] >>> Logging.make).build.useNow
  }

  val handWrittenFilterFunction: (LogContext, => String) => Boolean = { (ctx, _) =>
    val level = ctx.get(LogAnnotation.Level)
    ctx.get(LogAnnotation.Name) match {
      case List("a", "b", "c") => level >= LogLevel.Info
      case List("a", "b", "d") => level >= LogLevel.Warn
      case List("e")           => level >= LogLevel.Info
      case _                   => level >= LogLevel.Debug
    }
  }
  val handWrittenFilteredAppender: ZLayer[Any, Nothing, Appender[String]] =
    LogAppender
      .ignore[String]
      .withFilter(handWrittenFilterFunction)

  val handWrittenFilteredLogging: Logging =
    runtime.unsafeRun {
      (handWrittenFilteredAppender >>> Logging.make).build.useNow
    }

  val filterTreeFunction                                         =
    filterBy(LogLevel.Debug, "a.b.c" -> LogLevel.Info, "a.b.d" -> LogLevel.Warn, "e" -> LogLevel.Info)
  val filterTreeAppender: ZLayer[Any, Nothing, Appender[String]] =
    LogAppender
      .ignore[String]
      .withFilter(filterTreeFunction)

  val filterTreeLogging: Logging =
    runtime.unsafeRun {
      (filterTreeAppender >>> Logging.make).build.useNow
    }

  val cachedFilterTreeLogging: Logging =
    runtime.unsafeRun {
      for {
        cache <- TMap.empty[(List[String], LogLevel), Boolean].commit
        appender = LogAppender
          .ignore[String]
          .withFilterM(cachedFilterBy(cache, LogLevel.Debug, "a.b.c" -> LogLevel.Info, "a.b.d" -> LogLevel.Warn, "e" -> LogLevel.Info))
        logging <- (appender >>> Logging.make).build.useNow
      } yield logging
    }

  val names = List(
    List("a"),
    List("a", "b"),
    List("a", "b", "c"),
    List("a", "b", "d"),
    List("a", "b", "e"),
    List("a", "b", "e", "f", "g"),
    List("a", "z"),
    List("e"),
    List("e", "f"),
    List("e", "f"),
    List("a", "e", "f")
  )

  val rnd = new Random(12345)

  def testLoggingWith(logging: Logging): Unit = {
    val name = names(rnd.nextInt(names.length))
    runtime.unsafeRun {
      log
        .locally(_.annotate(LogAnnotation.Name, name)) {
          log.debug("test")
        }
        .provide(logging)
    }
  }

  def testEvalWith(f: (LogContext, => String) => Boolean): Boolean = {
    val name = names(rnd.nextInt(names.length))
    val ctx  = LogContext.empty
      .annotate(LogAnnotation.Name, name)
      .annotate(LogAnnotation.Level, LogLevel.Debug)
    f(ctx, "test")
  }

  /**
   * 3/1/2021 Initial results
   * FilterBenchmarks.handWrittenFilterEval     thrpt    5  9177150.646 ± 125715.644  ops/s
   * FilterBenchmarks.filterTreeEval            thrpt    5  7298406.870 ±  87773.959  ops/s

   * FilterBenchmarks.noFilteringLogging        thrpt    5   267066.692 ±   2170.339  ops/s
   * FilterBenchmarks.handWrittenFilterLogging  thrpt    5   262466.006 ±   3641.051  ops/s
   * FilterBenchmarks.filterTreeLog             thrpt    5   252841.756 ±   2912.062  ops/s
   * FilterBenchmarks.cachedFilterTreeLog       thrpt    5   260752.769 ±   2625.707  ops/s
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
    testLoggingWith(filterTreeLogging)

  @Benchmark
  def handWrittenFilterEval(): Boolean =
    testEvalWith(handWrittenFilterFunction)

  @Benchmark
  def filterTreeEval(): Boolean =
    testEvalWith(filterTreeFunction)
}

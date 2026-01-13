/*
 * Copyright 2019-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      .filter(filter)
      .install
  }

  val filterConfig: LogFilter.LogLevelByNameConfig = LogFilter.LogLevelByNameConfig(
    LogLevel.Debug,
    "a.b.c" -> LogLevel.Info,
    "a.b.d" -> LogLevel.Warning,
    "e"     -> LogLevel.Info,
    "f.g"   -> LogLevel.Error,
    "f"     -> LogLevel.Info
  )

  val filterByLogLevelAndNameLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> makeSystemOutLogger(LogFormat.default.toLogger)
      .filter(filterConfig.toFilter)
      .install

  val cachedFilterByLogLevelAndNameLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> makeSystemOutLogger(LogFormat.default.toLogger)
      .filter(filterConfig.toFilter.cached)
      .install

  val reconfigurableFilterByLogLevelAndNameLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> ReconfigurableLogger
      .make[Any, Nothing, String, Any, ConsoleLoggerConfig](
        ZIO.succeed(ConsoleLoggerConfig(LogFormat.default, filterConfig)),
        (config, _) => makeSystemOutLogger(config.format.toLogger).filter(config.toFilter)
      )
      .installUnscoped[Any]

  val reconfigurableCachedFilterByLogLevelAndNameLogging: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> ReconfigurableLogger
      .make[Any, Nothing, String, Any, ConsoleLoggerConfig](
        ZIO.succeed(ConsoleLoggerConfig(LogFormat.default, filterConfig)),
        (config, _) => makeSystemOutLogger(config.format.toLogger).filter(config.toFilter.cached)
      )
      .installUnscoped[Any]

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
   * 2023/12/26 Initial results
   *
   * jmh:run -i 3 -wi 3 -f1 -t1 .*FilterBenchmarks.*
   *
   *  Benchmark                                                         Mode  Cnt      Score       Error  Units
   *  FilterBenchmarks.cachedFilterByLogLevelAndNameLog                thrpt    3  14795.547 ±  1372.850  ops/s
   *  FilterBenchmarks.filterByLogLevelAndNameLog                      thrpt    3  15093.994 ±  1230.494  ops/s
   *  FilterBenchmarks.handWrittenFilterLog                            thrpt    3  13157.888 ± 10193.287  ops/s
   *  FilterBenchmarks.noFilteringLog                                  thrpt    3  11043.746 ±   230.514  ops/s
   *  FilterBenchmarks.reconfigurableCachedFilterByLogLevelAndNameLog  thrpt    3   7532.412 ±   415.760  ops/s
   *  FilterBenchmarks.reconfigurableFilterByLogLevelAndNameLog        thrpt    3   7482.096 ±   628.534  ops/s
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

  @Benchmark
  def reconfigurableCachedFilterByLogLevelAndNameLog(): Unit =
    testLoggingWith(reconfigurableCachedFilterByLogLevelAndNameLogging)

}

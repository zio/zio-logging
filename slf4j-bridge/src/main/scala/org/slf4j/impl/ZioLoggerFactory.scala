package org.slf4j.impl

import org.slf4j.{ ILoggerFactory, Logger }
import zio.ZIO
import zio.logging.Logging

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

class ZioLoggerFactory extends ILoggerFactory {
  private var runtime: zio.Runtime[Logging] = null
  private val loggers                       = new ConcurrentHashMap[String, Logger]().asScala

  def attachRuntime(runtime: zio.Runtime[Logging]): Unit =
    this.runtime = runtime

  private[impl] def run[A](f: ZIO[Logging, Nothing, A]): Unit =
    if (runtime != null) {
      runtime.unsafeRun(f)
      ()
    }

  override def getLogger(name: String): Logger =
    loggers.getOrElseUpdate(name, new ZioLogger(name, this))
}

object ZioLoggerFactory {
  def bind(runtime: zio.Runtime[Logging]): Unit =
    StaticLoggerBinder.getSingleton.getLoggerFactory
      .asInstanceOf[ZioLoggerFactory]
      .attachRuntime(runtime)
}

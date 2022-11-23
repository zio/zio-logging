package org.slf4j

import com.github.ghik.silencer.silent
import org.slf4j.zio.ZioLogger

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

class ZioLoggerFactory extends ILoggerFactory {
  private var runtime: _root_.zio.Runtime[Any] = null
  private val loggers                   = new ConcurrentHashMap[String, Logger]().asScala: @silent("JavaConverters")

  def attachRuntime(runtime: _root_.zio.Runtime[Any]): Unit =
    this.runtime = runtime

  private[slf4j] def run(f: _root_.zio.ZIO[Any, Nothing, Any]): Unit =
    if (runtime != null) {
      _root_.zio.Unsafe.unsafe { implicit u =>
        runtime.unsafe.run(f)
        ()
      }
    }

  override def getLogger(name: String): Logger =
    loggers.getOrElseUpdate(name, new ZioLogger(name, this))
}

object ZioLoggerFactory {
  def initialize(runtime: _root_.zio.Runtime[Any]): Unit =  {
    LoggerFactory.getProvider().getLoggerFactory()
          .asInstanceOf[ZioLoggerFactory]
          .attachRuntime(runtime)
  }
}

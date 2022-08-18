package zio.logging.backend

import java.util.concurrent.ConcurrentHashMap

class TestJPLoggerFinder extends System.LoggerFinder {
  override def getLogger(name: String, module: Module): System.Logger =
    TestJPLoggerFinder.loggers.computeIfAbsent(name, n => new TestJPLogger(n))
}

object TestJPLoggerFinder {
  private val loggers = new ConcurrentHashMap[String, TestJPLogger]()
}

package zio.logging

import zio._
import zio.logging.LogFormat.KeyValueLogFormat
import zio.logging.internal.LogAppender

object JsonLogFormat {
  def jsonArr(elements: LogFormat*): JsonLogFormat =
    (builder: LogAppender) =>
      LogFormat.make { (builder, trace, fiberId, logLevel, message, context, spans, location, annotations) =>
        builder.appendText("[")
        separated(elements.map(elem => jsonVal(elem)))
          .unsafeFormat(builder)(trace, fiberId, logLevel, message, context, spans, location, annotations)
        builder.appendText("]")
      }.unsafeFormat(builder)

  def jsonObj(entries: KeyValueLogFormat*): JsonLogFormat =
    (builder: LogAppender) =>
      LogFormat.make { (builder, trace, fiberId, logLevel, message, context, spans, location, annotations) =>
        builder.appendText("{")
        separated(entries)
          .unsafeFormat(builder)(trace, fiberId, logLevel, message, context, spans, location, annotations)
        builder.appendText("}")
      }.unsafeFormat(builder)

  private def jsonVal(value: LogFormat): LogFormat =
    LogFormat.make { (builder, trace, fiberId, logLevel, message, context, spans, location, annotations) =>
      builder.appendElement(
        value.unsafeFormat(_)(trace, fiberId, logLevel, message, context, spans, location, annotations)
      )
    }

  private final def separated(elements: Seq[LogFormat]): LogFormat =
    LogFormat.make { (builder, trace, fiberId, logLevel, message, context, spans, location, annotations) =>
      var separate = false
      for (e <- elements) {
        val str = builder.asString(a =>
          e.unsafeFormat(a)(trace, fiberId, logLevel, message, context, spans, location, annotations)
        )
        if (str.nonEmpty) {
          if (separate) builder.appendSeparator()
          builder.appendText(str)
          separate = true
        }
      }
    }

  private class JsonLogAppender extends LogAppender { self =>
    private val sb   = new StringBuilder
    private var leaf = true

    def appendCause(cause: Cause[Any]): Unit = appendText(cause.prettyPrint)

    override def appendElement(appendValue: LogAppender => Unit): Unit = {
      val appender    = new JsonLogAppender
      appendValue(appender)
      val appendedStr = appender.toString()
      if (appendedStr.nonEmpty) {
        if (appender.leaf) {
          // Leaf nodes should be escaped and quoted
          sb.append(JsonEscape.jsonEscapedQuoted(appendedStr))
        } else {
          sb.append(appendedStr)
        }
      }

      self.leaf = false
    }

    def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)

    def appendSeparator(): Unit = appendText(",")

    def appendText(text: String): Unit = { sb.append(text); () }

    def asString(appendValue: LogAppender => Unit): String = {
      val a = new JsonLogAppender
      appendValue(a)
      self.leaf = a.leaf
      a.toString()
    }

    def closeKeyOpenValue(): Unit = appendText("\":")

    def closeValue(): Unit = ()

    def openKey(): Unit = appendText("\"")

    override def toString(): String = sb.toString()
  }

  trait JsonLogFormat extends LogFormat {
    override def toLogger: ZLogger[String, String] = (
      trace: ZTraceElement,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => String,
      context: Map[ZFiberRef.Runtime[_], AnyRef],
      spans: List[LogSpan],
      location: ZTraceElement,
      annotations: Map[String, String]
    ) => {
      val appender = new JsonLogAppender
      unsafeFormat(appender)(
        trace,
        fiberId,
        logLevel,
        message,
        context,
        spans,
        location,
        annotations
      )
      appender.toString()
    }
  }
}

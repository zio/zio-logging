package zio.logging

import zio._
import zio.logging.JsonEscape.jsonEscapedQuoted
import zio.logging.LogFormat._
import zio.logging.internal.LogAppender

trait JsonLogFormat extends LogFormat {
//  private def jsonAppender(textAppender: String => Any): LogAppender = new LogAppender { self =>
//    def appendCause(cause: Cause[Any]): Unit = appendText(cause.prettyPrint)
//
//    def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)
//
//    def appendText(text: String): Unit = { textAppender(text); () }
//
//    def closeKeyOpenValue(): Unit = appendText(":")
//
//    def closeValue(): Unit = ()
//
//    def openKey(): Unit = ()
//  }
//
//  /**
//   * Converts this log format into a text logger, which accepts text input, and
//   * produces text output.
//   */
//  override def toLogger: ZLogger[String, String] = (
//    trace: ZTraceElement,
//    fiberId: FiberId,
//    logLevel: LogLevel,
//    message: () => String,
//    context: Map[ZFiberRef.Runtime[_], AnyRef],
//    spans: List[LogSpan],
//    location: ZTraceElement,
//    annotations: Map[String, String]
//  ) => {
//    val builder = new StringBuilder()
//    unsafeFormat(jsonAppender(builder.append(_)))(
//      trace,
//      fiberId,
//      logLevel,
//      message,
//      context,
//      spans,
//      location,
//      annotations
//    )
//    builder.toString()
//  }
}

object JsonLogFormat {
  sealed trait JsonNode
  case class JsonObject(kv: (String, JsonNode)*) extends JsonNode
  case class JsonArray(items: Seq[JsonNode])     extends JsonNode
  case class JsonValue(fmt: LogFormat)           extends JsonNode

  def json(json: JsonNode): LogFormat = {
    def build(node: JsonNode): LogFormat = node match {
      case JsonObject(kvs @ _*) =>
        text("{") + kvs
          .map(kv => text(jsonEscapedQuoted(kv._1)) + text(":") + build(kv._2))
          .reduce((a, b) => a + text(",") + b) + text("}")
      case JsonArray(items)     =>
        text("[") + items.map(build).reduce((a, b) => a + text(",") + b) + text("]")
      case JsonValue(fmt)       => jsonText(fmt)
    }

    build(json)
  }

  private def jsonText(value: LogFormat): LogFormat =
    LogFormat.make { (builder, trace, fiberId, level, line, fiberRefs, spans, location, annotations) =>
      val tempBuilder = new StringBuilder
      val append      = LogAppender.unstructured { (text: String) =>
        tempBuilder.append(jsonEscapedQuoted(text))
        ()
      }
      value.unsafeFormat(append)(trace, fiberId, level, line, fiberRefs, spans, location, annotations)
      builder.appendText(tempBuilder.toString())
    }

  private def jsonPair(value: LogFormat): LogFormat =
    LogFormat.make { (builder, trace, fiberId, level, line, fiberRefs, spans, location, annotations) =>
      val tempBuilder = new StringBuilder
      val append      = LogAppender.unstructured { (text: String) =>
        tempBuilder.append(jsonEscapedQuoted(text))
        ()
      }
      value.unsafeFormat(append)(trace, fiberId, level, line, fiberRefs, spans, location, annotations)
      builder.appendText(tempBuilder.toString())
    }
}

package zio.logging.internal

import zio.Cause
import zio.logging.JsonEscape

private[logging] class JsonLogAppender extends LogAppender {
  self =>
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

  def appendText(text: String): Unit = {
    sb.append(text); ()
  }

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

  def openMap(): Unit = appendText("{")

  def closeMap(): Unit = appendText("}")

  def openSeq(): Unit = appendText("[")

  def closeSeq(): Unit = appendText("]")
}

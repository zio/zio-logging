/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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
package zio.logging.internal

import zio._

import scala.collection.mutable

/**
 * A [[LogAppender]] is a low-level interface designed to be the bridge between
 * ZIO Logging and logging backends, such as Logback. This interface is slightly
 * higher-level than a string builder, because it allows for structured logging,
 * and preserves all ZIO-specific information about runtime failures.
 */
private[logging] trait LogAppender { self =>

  /**
   * Appends a [[zio.Cause]] to the log. Some logging backends may have
   * special support for logging failures.
   */
  def appendCause(cause: Cause[Any]): Unit

  /**
   * Appends a numeric value to the log.
   */
  def appendNumeric[A](numeric: A): Unit

  /**
   * Appends unstructured text to the log.
   */
  def appendText(text: String): Unit

  /**
   * Appends a key/value string pair to the log.
   */
  final def appendKeyValue(key: String, value: String): Unit = appendKeyValue(key, _.appendText(value))

  /**
   * Appends a key/value pair, with the value it created with the log appender.
   */
  final def appendKeyValue(key: String, appendValue: LogAppender => Unit): Unit = {
    openKey()
    try appendText(key)
    finally closeKeyOpenValue()
    try appendValue(self)
    finally closeValue()
  }

  /**
   * Marks the close of a key for a key/value pair, and the opening of the value.
   */
  def closeKeyOpenValue(): Unit

  /**
   * Marks the close of the log entry
   */
  def closeLogEntry(): Unit

  /**
   * Marks the close of the value of a key/value pair.
   */
  def closeValue(): Unit

  /**
   * Marks the open of the key.
   */
  def openKey(): Unit

  /**
   * Marks the start of the log entry
   */
  def openLogEntry(): Unit

  /**
   * Modifies the way text is appended to the log.
   */
  final def withAppendText(f: (String => Unit) => (String => Unit)): LogAppender = new LogAppender.Proxy(self) {
    val decorated: String => Unit = f(self.appendText(_))

    override def appendText(text: String): Unit = decorated(text)
  }
}
private[logging] object LogAppender {
  class Proxy(self: LogAppender) extends LogAppender {
    def appendCause(cause: Cause[Any]): Unit = self.appendCause(cause)

    def appendNumeric[A](numeric: A): Unit = self.appendNumeric(numeric)

    def appendText(text: String): Unit = self.appendText(text)

    def closeKeyOpenValue(): Unit = self.closeKeyOpenValue()

    def closeLogEntry(): Unit = self.closeLogEntry()

    def closeValue(): Unit = self.closeValue()

    def openKey(): Unit = self.openKey()

    def openLogEntry(): Unit = self.openLogEntry()
  }

  /**
   * A [[LogAppender]] for unstructured logging, which simply turns everything
   * into text, and passes it to the given text appender function.
   */
  def unstructured(textAppender: String => Any): LogAppender = new LogAppender { self =>
    def appendCause(cause: Cause[Any]): Unit = appendText(cause.prettyPrint)

    def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)

    def appendText(text: String): Unit = { textAppender(text); () }

    def closeKeyOpenValue(): Unit = appendText("=")

    def closeLogEntry(): Unit = ()

    def closeValue(): Unit = ()

    def openKey(): Unit = ()

    def openLogEntry(): Unit = ()
  }

  def json(textAppender: String => Any): LogAppender = new LogAppender { self =>
    class State(
      var root: Boolean = false,
      var separateKeyValue: Boolean = false,
      var writingKey: Boolean = false,
      val content: mutable.StringBuilder = new mutable.StringBuilder,
      var textContent: mutable.StringBuilder = new mutable.StringBuilder
    ) {
      def appendContent(str: CharSequence): Unit     = { content.append(str); () }
      def appendTextContent(str: CharSequence): Unit = { textContent.append(str); () }
    }

    val stack = new mutable.Stack[State]()

    def current: State = stack.top

    override def appendCause(cause: Cause[Any]): Unit = appendText(cause.prettyPrint)

    override def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)

    override def appendText(text: String): Unit =
      if (current.writingKey) current.appendContent(text)
      else current.appendTextContent(text)

    def beginStructure(root: Boolean = false): Unit = { stack.push(new State(root = root)); () }

    def endStructure(): CharSequence = {
      val result = new mutable.StringBuilder

      val cleanedTextContent = {
        // Do a little cleanup to handle default log formats (quoted and spaced)
        if (current.textContent.startsWith("\"") && current.textContent.endsWith("\""))
          current.textContent = current.textContent.drop(1).dropRight(1)
        if (current.textContent.forall(_ == ' ')) current.textContent.clear()
        current.textContent.toString()
      }

      if (current.content.isEmpty && !current.root) {
        // Simple value
        result.append("\"").append(JsonEscape(cleanedTextContent)).append("\"")
      } else {
        // Structure
        result.append("{")

        if (current.textContent.nonEmpty) {
          result.append(""""text_content":""")
          result.append("\"").append(JsonEscape(cleanedTextContent)).append("\"")
        }

        if (current.content.nonEmpty) {
          if (current.textContent.nonEmpty) result.append(",")
          result.append(current.content)
        }

        result.append("}")
      }

      stack.pop()
      result
    }

    override def closeKeyOpenValue(): Unit = {
      current.writingKey = false
      current.appendContent("""":""")
      beginStructure()
    }

    override def closeLogEntry(): Unit = {
      textAppender(endStructure().toString)
      ()
    }

    override def closeValue(): Unit = {
      val result = endStructure()
      current.appendContent(result)
    }

    override def openKey(): Unit = {
      if (current.separateKeyValue) current.appendContent(",")
      current.separateKeyValue = true
      current.writingKey = true
      current.appendContent("\"")
    }

    override def openLogEntry(): Unit = {
      stack.clear()
      beginStructure(true)
    }
  }
}

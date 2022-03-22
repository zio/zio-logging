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

  def appendElement(appendValue: LogAppender => Unit): Unit =
    appendValue(self)

  /**
   * Appends a numeric value to the log.
   */
  def appendNumeric[A](numeric: A): Unit

  /**
   * Adds a separator between elements
   */
  def appendSeparator(): Unit

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
    try appendElement(appendValue)
    finally closeValue()
  }

  def asString(appendValue: LogAppender => Unit): String

  /**
   * Marks the close of a key for a key/value pair, and the opening of the value.
   */
  def closeKeyOpenValue(): Unit

  /**
   * Marks the close of the value of a key/value pair.
   */
  def closeValue(): Unit

  /**
   * Marks the open of the key.
   */
  def openKey(): Unit

  /**
   * Marks the open of a map of key/value pairs
   */
  def openMap(): Unit

  /**
   * Marks the close of a map of key/value pairs
   */
  def closeMap(): Unit

  /**
   * Marks the open of a sequence of elements
   */
  def openSeq(): Unit

  /**
   * Marks the close of a sequence of elements
   */
  def closeSeq(): Unit

  /**
   * Modifies the way text is appended to the log.
   */
  final def withAppendText(f: (String => Unit) => (String => Unit)): LogAppender = new LogAppender.Proxy(self) {
    val decorated: String => Unit = f(self.appendText(_))

    override def appendText(text: String): Unit = decorated(text)
  }
}
private[logging] object LogAppender {
  private[logging] class Proxy(self: LogAppender) extends LogAppender {
    def appendCause(cause: Cause[Any]): Unit = self.appendCause(cause)

    def appendNumeric[A](numeric: A): Unit = self.appendNumeric(numeric)

    def appendSeparator(): Unit = self.appendSeparator()

    def appendText(text: String): Unit = self.appendText(text)

    def asString(appendValue: LogAppender => Unit): String = self.asString(appendValue)

    def closeKeyOpenValue(): Unit = self.closeKeyOpenValue()

    def closeValue(): Unit = self.closeValue()

    def openKey(): Unit = self.openKey()

    def openMap(): Unit = self.openMap()

    def closeMap(): Unit = self.closeMap()

    def openSeq(): Unit = self.openSeq()

    def closeSeq(): Unit = self.closeSeq()
  }

  /**
   * A [[LogAppender]] for unstructured logging, which simply turns everything
   * into text, and passes it to the given text appender function.
   */
  def unstructured(textAppender: String => Any): LogAppender = new LogAppender { self =>
    def appendCause(cause: Cause[Any]): Unit = appendText(cause.prettyPrint)

    def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)

    def appendSeparator(): Unit = appendText(" ")

    def appendText(text: String): Unit = { textAppender(text); () }

    def asString(appendValue: LogAppender => Unit): String = {
      val sb = new StringBuilder
      LogAppender.unstructured(sb.append(_))
      sb.toString()
    }

    def closeKeyOpenValue(): Unit = appendText("=")

    def closeValue(): Unit = ()

    def openKey(): Unit = ()

    def openMap(): Unit = ()

    def closeMap(): Unit = ()

    def openSeq(): Unit = ()

    def closeSeq(): Unit = ()
  }
}

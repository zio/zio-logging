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
package zio.logging

import zio._

/**
 * A [[LogAppender]] is a low-level interface designed to be the bridge between
 * ZIO Logging and logging backends, such as Logback. This interface is slightly
 * higher-level than a string builder, because it allows for structured loggers.
 */
private[logging] trait LogAppender  { self =>
  def appendCause(cause: Cause[Any]): Unit

  def appendNumeric[A](numeric: A): Unit

  def appendText(text: String): Unit

  def appendKeyValue(key: String, value: String): Unit

  def closeKeyOpenValue(): Unit

  def closeValue(): Unit

  def openKeyName(): Unit

  final def withAppendText(f: (String => Unit) => (String => Unit)): withAppendText = new withAppendText(f)
  class withAppendText(f: (String => Unit) => (String => Unit)) extends LogAppender {
    val decorated: String => Unit = f(self.appendText(_))

    def appendCause(cause: Cause[Any]): Unit = appendText(cause.prettyPrint)

    def appendNumeric[A](numeric: A): Unit = self.appendNumeric(numeric)

    def appendText(text: String): Unit = decorated(text)

    def appendKeyValue(key: String, value: String): Unit = self.appendKeyValue(key, value)

    def closeKeyOpenValue(): Unit = self.closeKeyOpenValue()

    def closeValue(): Unit = self.closeValue()

    def openKeyName(): Unit = self.openKeyName()
  }
}
private[logging] object LogAppender {
  def unstructured(fn: String => Any): LogAppender = new LogAppender { self =>
    def appendCause(cause: Cause[Any]): Unit = appendText(cause.prettyPrint)

    def appendNumeric[A](numeric: A): Unit = appendText(numeric.toString)

    def appendText(text: String): Unit = { fn(text); () }

    def appendKeyValue(key: String, value: String): Unit = {
      openKeyName()
      try appendText(key)
      finally closeKeyOpenValue()
      try appendText(value)
      finally closeValue()
    }

    def closeKeyOpenValue(): Unit = appendText("=")

    def closeValue(): Unit = ()

    def openKeyName(): Unit = ()
  }
}

package zio

package object logging {
  type Logging     = Has[Logger[String]]
  type Appender[A] = Has[LogAppender.Service[A]]
}

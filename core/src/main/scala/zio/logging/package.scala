package zio

package object logging {
  type Logging        = Has[Logger[String]]
  type LogAppender[A] = Has[LogAppender.Service[A]]
}

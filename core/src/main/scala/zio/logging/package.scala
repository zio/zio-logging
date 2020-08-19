package zio

package object logging {
  type Logging        = Has[Logger[String]]
}

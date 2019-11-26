package zio.logging

trait Logging extends AbstractLogging[String]

object Logging {
  type Service[-R] = AbstractLogging.Service[R, String]
}

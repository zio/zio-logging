package zio.logging

trait Logging extends AbstractLogging

object Logging {
  type Service[-R] = AbstractLogging.Service[R]
}

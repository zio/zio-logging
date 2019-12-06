package zio.logging.slf4j

import zio.logging.AbstractLogging

trait Logging extends AbstractLogging[String]

object Logging {
  type Service[-R] = AbstractLogging.Service[R, String]
}

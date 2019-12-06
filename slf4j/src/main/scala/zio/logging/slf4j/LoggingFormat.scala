package zio.logging.slf4j

import zio._
trait LoggingFormat {
  def format(message: String): ZIO[Any, Nothing, String]
}

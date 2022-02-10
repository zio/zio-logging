package zio.logging.slf4j.bridge

final case class WrappedException(message: String, failure: Throwable) extends RuntimeException(message, failure)

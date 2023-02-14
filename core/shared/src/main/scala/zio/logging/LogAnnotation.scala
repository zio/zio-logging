package zio.logging

import com.github.ghik.silencer.silent
import zio.Cause

import java.time.OffsetDateTime
import java.{ util => ju }
import scala.reflect.ClassTag

/**
 * A `LogAnnotation` describes a particular type of annotation applied to log
 * lines.
 */
final case class LogAnnotation[A: ClassTag](name: String, initialValue: A, combine: (A, A) => A, render: A => String) {
  self =>

  def apply(value: A): LogContext => LogContext = _.annotate(self, combine(initialValue, value))

  def id: (String, ClassTag[A]) = (name, classTag)

  /**
   * The class tag of the annotation type, used for disambiguation purposes only.
   */
  def classTag: ClassTag[A] = implicitly[ClassTag[A]]

  override def hashCode: Int = id.hashCode

  override def equals(that: Any): Boolean =
    that match {
      case that: LogAnnotation[_] => self.id == that.id
      case _                      => false
    }

  override def toString: String = "LogAnnotation(" + name + ")"
}

object LogAnnotation {

  /**
   * Creates a LogAnnotation that is represented as an optional value and initialized with `None`.
   * If a value for the annotation is present, it will be rendered using the provided function. When
   * absent, it will be rendered as an empty string.
   */
  @silent("evidence")
  def optional[A: ClassTag](name: String, render: A => String): LogAnnotation[Option[A]] =
    LogAnnotation(
      name = name,
      initialValue = None,
      combine = (_, a) => a,
      render = {
        case None    => ""
        case Some(a) => render(a)
      }
    )

  /**
   * The `CorrelationId` annotation keeps track of correlation id.
   */
  val CorrelationId: LogAnnotation[Option[ju.UUID]] = LogAnnotation[Option[java.util.UUID]](
    name = "correlation-id",
    initialValue = None,
    combine = (_, r) => r,
    render = _.map(_.toString).getOrElse("undefined-correlation-id")
  )

  /**
   * The `Level` annotation keeps track of log levels.
   */
  val Level: LogAnnotation[LogLevel] = LogAnnotation[LogLevel]("level", LogLevel.Info, (_, r) => r, _.render)

  /**
   * The `Name` annotation keeps track of logger names.
   */
  val Name: LogAnnotation[List[String]] = LogAnnotation[List[String]]("name", Nil, _ ++ _, _.mkString("."))

  /**
   * The `Throwable` annotation keeps track of a throwable.
   */
  val Throwable: LogAnnotation[Option[Throwable]] =
    optional[Throwable](
      name = "throwable",
      zio.Cause.fail(_).prettyPrint
    )

  /**
   * The `Cause` annotation keeps track of a Cause.
   */
  val Cause: LogAnnotation[Option[Cause[Any]]] =
    optional[Cause[Any]](
      name = "cause",
      _.prettyPrint
    )

  /**
   * Log timestamp
   */
  val Timestamp: LogAnnotation[OffsetDateTime] = LogAnnotation[OffsetDateTime](
    name = "timestamp",
    initialValue = OffsetDateTime.MIN,
    combine = (_, newValue) => newValue,
    render = (time: OffsetDateTime) => LogDatetimeFormatter.humanReadableDateTimeFormatter.format(time)
  )
}

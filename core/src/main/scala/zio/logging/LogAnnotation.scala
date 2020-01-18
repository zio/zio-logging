package zio.logging

import scala.reflect.ClassTag

/**
 * A `LogAnnotation` describes a particular type of annotation applied to log
 * lines.
 */
final case class LogAnnotation[A: ClassTag](name: String, neutral: A, combine: (A, A) => A, render: A => String) {
  self =>
  def id: (String, ClassTag[A]) = (name, classTag)

  /**
   * The class tag of the annotation type, used for disambiguation purposes only.
   */
  def classTag: ClassTag[A] = implicitly[ClassTag[A]]

  override def hashCode: Int = id.hashCode

  override def equals(that: Any): Boolean = that match {
    case that: LogAnnotation[_] => self.id == that.id
    case _                      => false
  }
}

object LogAnnotation {

  /**
   * The `Level` annotation keeps track of log levels.
   */
  val Level = LogAnnotation[LogLevel]("level", LogLevel.Off, (_, r) => r, _.render)

  /**
   * The `Name` annotation keeps track of logger names.
   */
  val Name = LogAnnotation[List[String]]("name", Nil, _ ++ _, _.mkString("."))
}

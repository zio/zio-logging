package zio.logging

final case class LogFormatType[A](unsafeMake: () => LogFormatBuilder[A, A])
object LogFormatType {
  val string: LogFormatType[String] = LogFormatType { () =>
    val builder = new StringBuilder()
    val append  = (line: String) => {
      builder.append(line)
      ()
    }
    LogFormatBuilder(append, () => builder.toString())
  }
}

final case class LogFormatBuilder[-In, +Out](unsafeAppend: In => Unit, unsafeResult: () => Out)

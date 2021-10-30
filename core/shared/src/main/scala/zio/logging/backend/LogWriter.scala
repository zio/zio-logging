package zio.logging.backend

trait LogWriter  {
  def apply(line: String): Unit
}
object LogWriter {
  val console: LogWriter = new LogWriter {
    override def apply(line: String): Unit = println(line)
  }

  val consoleErr: LogWriter = new LogWriter {
    override def apply(line: String): Unit = Console.err.println(line)
  }
}

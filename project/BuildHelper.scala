import sbt.Keys._
import sbt._

object BuildHelper {
  def skipScala3Docs = Seq(
    Compile / doc / sources := {
      val old = (Compile / doc / sources).value
      if (scalaBinaryVersion.value == "3") {
        Nil
      } else {
        old
      }
    }
  )

  def jpmsOverwriteModulePath(modulePaths: Seq[File])(options: Seq[String]): Seq[String] = {
    val modPathString = modulePaths.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
    val option        = "--module-path"
    val index         = options.indexWhere(_ == option)
    if (index == -1) options ++ List(option, modPathString)
    else options.patch(index + 1, List(modPathString), 1)
  }

}

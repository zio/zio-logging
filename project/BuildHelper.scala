import sbt._
import sbt.Keys._
import dotty.tools.sbtplugin.DottyPlugin.autoImport._

object BuildHelper {

  def stdSettings(prjName: String) =
    Seq(
      name := s"$prjName",
      crossScalaVersions := Seq(Scala211, Scala212, Scala213),
      scalaVersion in ThisBuild := Scala212,
      scalacOptions := CommonOpts ++ extraOptions(scalaVersion.value),
      libraryDependencies ++= {
        if (isDotty.value)
          Seq(
            ("com.github.ghik" % s"silencer-lib_$Scala213" % SilencerVersion % Provided)
              .withDottyCompat(scalaVersion.value)
          )
        else
          Seq(
            "com.github.ghik" % "silencer-lib" % SilencerVersion % Provided cross CrossVersion.full,
            compilerPlugin("com.github.ghik" % "silencer-plugin" % SilencerVersion cross CrossVersion.full)
          )
      },
      incOptions ~= (_.withLogRecompileOnMacro(false))
    )

  final private val Scala211 = "2.11.12"
  final private val Scala212 = "2.12.13"
  final private val Scala213 = "2.13.4"
  final private val Scala3   = "3.0.0-M3"

  final val scala3Settings = Seq(
    crossScalaVersions += Scala3
  )

  final val SilencerVersion = "1.7.2"

  final private val CommonOpts =
    Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-language:higherKinds",
      "-language:existentials",
      "-unchecked",
      "-deprecation",
      "-Xfatal-warnings"
    )

  final private val CommonOpts2x =
    Seq(
      "-explaintypes",
      "-Yrangepos",
      "-Xlint:_,-type-parameter-shadow",
      "-Xsource:2.13",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    )

  final private val Opts213 =
    CommonOpts2x ++ Seq(
      "-Wunused:imports",
      "-Wvalue-discard",
      "-Wunused:patvars",
      "-Wunused:privates",
      "-Wunused:params",
      "-Wvalue-discard",
      "-Wdead-code",
      "-P:silencer:globalFilters=[import scala.collection.compat._]"
    )

  final private val OptsTo212 =
    CommonOpts2x ++ Seq(
      "-Xfuture",
      "-Ypartial-unification",
      "-Ywarn-nullary-override",
      "-Yno-adapted-args",
      "-Ywarn-infer-any",
      "-Ywarn-inaccessible",
      "-Ywarn-nullary-unit",
      "-Ywarn-unused-import"
    )

  final private val OptsTo3 =
    Seq(
      "-noindent"
    )

  private def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 13)) =>
        Opts213
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:<source>"
        ) ++ OptsTo212
      case Some((3, 0))  =>
        OptsTo3
      case _             =>
        Seq("-Xexperimental") ++ OptsTo212
    }
}

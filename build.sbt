import BuildHelper._
import sbtcrossproject.CrossPlugin.autoImport.{ CrossType, crossProject }

name := "zio-logging"

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.github.io/zio-logging/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers   := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net")),
      Developer(
        "pshemass",
        "Przemyslaw Wierzbicki",
        "rzbikson@gmail.com",
        url("https://github.com/pshemass")
      )
    )
  )
)

val ZioVersion           = "2.0.0-RC6"
val scalaJavaTimeVersion = "2.3.0"
val slf4jVersion         = "1.7.35"

addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check")

addCommandAlias(
  "testJVM",
  ";coreJVM/test;slf4j/test"
)

addCommandAlias(
  "testJS",
  ";coreJS/test"
)

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(coreJVM, coreJS, slf4j, benchmarks, docs, examples)

lazy val core    = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .settings(stdSettings("zio-logging"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio"          % ZioVersion,
      "dev.zio" %%% "zio-streams"  % ZioVersion,
      "dev.zio" %%% "zio-test"     % ZioVersion % Test,
      "dev.zio" %%% "zio-test-sbt" % ZioVersion % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .jvmSettings(
    Test / fork := true,
    run / fork  := true
  )
lazy val coreJVM = core.jvm
  .settings(dottySettings)
lazy val coreJS  = core.js.settings(
  libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.3.0" % Test
)

lazy val slf4j = project
  .in(file("slf4j"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-slf4j"))
  .settings(dottySettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"            % "slf4j-api"                % slf4jVersion,
      "dev.zio"            %%% "zio-test"                 % ZioVersion % Test,
      "dev.zio"            %%% "zio-test-sbt"             % ZioVersion % Test,
      "ch.qos.logback"       % "logback-classic"          % "1.2.6"    % Test,
      "net.logstash.logback" % "logstash-logback-encoder" % "6.6"      % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val benchmarks = project
  .in(file("benchmarks"))
  .settings(stdSettings("zio-logging-benchmarks"))
  .settings(
    publish / skip := true,
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings"
  )
  .dependsOn(coreJVM)
  .enablePlugins(JmhPlugin)

lazy val docs = project
  .in(file("zio-logging-docs"))
  .settings(
    publish / skip                             := true,
    moduleName                                 := "zio-logging-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    crossScalaVersions --= List(ScalaDotty),
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(coreJVM, slf4j),
    ScalaUnidoc / unidoc / target              := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite                       := docusaurusCreateSite.dependsOn(Compile / unidoc).value,
    docusaurusPublishGhpages                   := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value
  )
  .dependsOn(coreJVM, slf4j)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)

lazy val examples = project
  .in(file("examples"))
  .dependsOn(slf4j)
  .settings(stdSettings("zio-logging-examples"))
  .settings(dottySettings)
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback"       % "logback-classic"          % "1.2.6",
      "net.logstash.logback" % "logstash-logback-encoder" % "6.6"
    )
  )

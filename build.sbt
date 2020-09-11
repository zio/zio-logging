import sbtcrossproject.CrossPlugin.autoImport.{ crossProject, CrossType }
import BuildHelper._

name := "zio-logging"

inThisBuild(
  List(
    organization := "dev.zio",
    homepage := Some(url("https://zio.github.io/zio-logging/")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net")),
      Developer(
        "pshemass",
        "Przemyslaw Wierzbicki",
        "rzbikson@gmail.com",
        url("https://github.com/pshemass")
      )
    ),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/zio/zio-logging/"),
        "scm:git:git@github.com:zio/zio-logging.git"
      )
    )
  )
)

ThisBuild / publishTo := sonatypePublishToBundle.value

val ZioVersion           = "1.0.1"
val scalaJavaTimeVersion = "2.0.0-RC5"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root = project
  .in(file("."))
  .settings(skip in publish := true)
  .aggregate(coreJVM, coreJS, slf4j, jsconsole, jshttp, examples, docs)

lazy val core    = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(stdSettings("zio-logging"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"                %%% "zio"                     % ZioVersion,
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.2.0",
      "dev.zio"                %%% "zio-test"                % ZioVersion % Test,
      "dev.zio"                %%% "zio-test-sbt"            % ZioVersion % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .jvmSettings(
    fork in Test := true,
    fork in run := true
  )
lazy val coreJVM = core.jvm
lazy val coreJS  = core.js.settings(
  libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test
)

lazy val slf4j = project
  .in(file("slf4j"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-slf4j"))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"            % "slf4j-api"                % "1.7.30",
      "dev.zio"            %%% "zio-test"                 % ZioVersion % Test,
      "dev.zio"            %%% "zio-test-sbt"             % ZioVersion % Test,
      "ch.qos.logback"       % "logback-classic"          % "1.2.3"    % Test,
      "net.logstash.logback" % "logstash-logback-encoder" % "6.4"      % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val jsconsole = project
  .in(file("jsconsole"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJS)
  .settings(stdSettings("zio-logging-jsconsole"))
  .settings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test,
      "dev.zio"           %%% "zio-test"        % ZioVersion           % Test,
      "dev.zio"           %%% "zio-test-sbt"    % ZioVersion           % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val jshttp = project
  .in(file("jshttp"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJS)
  .settings(stdSettings("zio-logging-jshttp"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "1.1.0"
    )
  )

lazy val docs = project
  .in(file("zio-logging-docs"))
  .settings(
    skip in publish := true,
    moduleName := "docs",
//    unusedCompileDependenciesFilter -= moduleFilter("org.scalameta", "mdoc"),
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    scalacOptions ~= { _.filterNot(_.startsWith("-Ywarn")) },
    scalacOptions ~= { _.filterNot(_.startsWith("-Xlint")) },
    libraryDependencies ++= Seq(
      ("com.github.ghik" % "silencer-lib" % SilencerVersion % Provided).cross(CrossVersion.full)
    )
  )
  .dependsOn(coreJVM, slf4j)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)

lazy val examples = project
  .in(file("examples"))
  .dependsOn(slf4j)
  .settings(stdSettings("zio-logging-examples"))
  .settings(
    skip in publish := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback"       % "logback-classic"          % "1.2.3",
      "net.logstash.logback" % "logstash-logback-encoder" % "6.4"
    )
  )

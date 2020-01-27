import BuildHelper._

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

val ZioVersion = "1.0.0-RC17"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root = project
  .in(file("."))
  .settings(skip in publish := true)
  .aggregate(core, slf4j, examples, docs)

lazy val core = project
  .in(file("core"))
  .settings(stdSettings("zio-logging"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"                %% "zio"                     % ZioVersion,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.3",
      "dev.zio"                %% "zio-test"                % ZioVersion % Test,
      "dev.zio"                %% "zio-test-sbt"            % ZioVersion % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val slf4j = project
  .in(file("slf4j"))
  .dependsOn(core)
  .settings(stdSettings("zio-logging-slf4j"))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "1.7.30"
    )
  )

lazy val docs = project
  .in(file("zio-logging-docs"))
  .settings(
    skip in publish := true,
    moduleName := "docs",
    unusedCompileDependenciesFilter -= moduleFilter("org.scalameta", "mdoc"),
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    scalacOptions ~= { _.filterNot(_.startsWith("-Ywarn")) },
    scalacOptions ~= { _.filterNot(_.startsWith("-Xlint")) },
    libraryDependencies ++= Seq(
      ("com.github.ghik" % "silencer-lib" % "1.4.4" % Provided).cross(CrossVersion.full)
    )
  )
  .dependsOn(core, slf4j)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)

lazy val examples = project
  .in(file("examples"))
  .dependsOn(slf4j)
  .settings(stdSettings("zio-logging-examples"))
  .settings(
    skip in publish := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    )
  )

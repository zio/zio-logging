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

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root = project
  .in(file("."))
  .settings(skip in publish := true)
  .aggregate(core, slf4j, examples)

lazy val core = project
  .in(file("core"))
  .settings(stdSettings("zio-logging"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"                %% "zio"                     % "1.0.0-RC17",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.2"
    )
  )

lazy val slf4j = project
  .in(file("slf4j"))
  .dependsOn(core)
  .settings(stdSettings("zio-logging-slf4j"))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "1.7.29"
    )
  )

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

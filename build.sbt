inThisBuild(
  List(
    organization := "dev.zio",
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
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    releaseEarlyWith := SonatypePublisher,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/zio/zio-logging/"),
        "scm:git:git@github.com:zio/zio-logging.git"
      )
    )
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val core = project
  .in(file("core"))
  .settings(
    name := "zio-logging",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "1.0.0-RC12-1"
    )
  )


lazy val root = project
  .in(file("."))
  .settings(skip in publish := true)
  .aggregate(core)

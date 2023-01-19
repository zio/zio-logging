import BuildHelper._
import MimaSettings.mimaSettings
import sbtcrossproject.CrossPlugin.autoImport.{ CrossType, crossProject }

name := "zio-logging"

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev/zio-logging/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers   := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net")),
      Developer(
        "pshemass",
        "Przemyslaw Wierzbicki",
        "rzbikson@gmail.com",
        url("https://github.com/pshemass")
      ),
      Developer("justcoon", "Peter Kotula", "peto.kotula@yahoo.com", url("https://github.com/justcoon"))
    )
  )
)

val ZioVersion           = "2.0.5"
val scalaJavaTimeVersion = "2.3.0"
val slf4jVersion         = "1.7.36"
val logbackVersion       = "1.2.11"

addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check")

addCommandAlias(
  "testJVM8",
  ";coreJVM/test;slf4j/test;slf4jBridge/test"
)

addCommandAlias(
  "testJVM",
  ";coreJVM/test;slf4j/test;jpl/test;slf4jBridge/test"
)

addCommandAlias(
  "testJS",
  ";coreJS/test"
)

addCommandAlias(
  "mimaChecks",
  "all coreJVM/mimaReportBinaryIssues slf4j/mimaReportBinaryIssues slf4jBridge/mimaReportBinaryIssues"
)

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(coreJVM, coreJS, slf4j, slf4jBridge, jpl, benchmarks, examples, docs)

lazy val core = crossProject(JSPlatform, JVMPlatform)
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
    run / fork  := true,
    mimaSettings(failOnProblem = true)
  )

lazy val coreJVM = core.jvm
lazy val coreJS  = core.js.settings(
  libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.4.0" % Test
)

lazy val slf4j = project
  .in(file("slf4j"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-slf4j"))
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"               % "slf4j-api"                % slf4jVersion,
      "dev.zio"               %%% "zio-test"                 % ZioVersion     % Test,
      "dev.zio"               %%% "zio-test-sbt"             % ZioVersion     % Test,
      "ch.qos.logback"          % "logback-classic"          % logbackVersion % Test,
      "net.logstash.logback"    % "logstash-logback-encoder" % "6.6"          % Test,
      "org.scala-lang.modules" %% "scala-collection-compat"  % "2.9.0"        % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val slf4jBridge = project
  .in(file("slf4j-bridge"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-slf4j-bridge"))
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"               % "slf4j-api"               % slf4jVersion,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.9.0",
      "dev.zio"               %%% "zio-test"                % ZioVersion % Test,
      "dev.zio"               %%% "zio-test-sbt"            % ZioVersion % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val jpl = project
  .in(file("jpl"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-jpl", "9"))
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % ZioVersion % Test,
      "dev.zio" %%% "zio-test-sbt" % ZioVersion % Test
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

lazy val examples = project
  .in(file("examples"))
  .dependsOn(slf4j, jpl)
  .settings(stdSettings("zio-logging-examples"))
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback"       % "logback-classic"          % logbackVersion,
      "net.logstash.logback" % "logstash-logback-encoder" % "6.6",
      "dev.zio"             %% "zio-metrics-connectors"   % "2.0.4",
      "dev.zio"            %%% "zio-test"                 % ZioVersion % Test,
      "dev.zio"            %%% "zio-test-sbt"             % ZioVersion % Test
    )
  )

lazy val docs = project
  .in(file("zio-logging-docs"))
  .settings(
    moduleName                                 := "zio-logging-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName                                := "ZIO Logging",
    badgeInfo                                  := Some(
      BadgeInfo(
        artifact = "zio-logging_2.12",
        projectStage = ProjectStage.ProductionReady
      )
    ),
    docsPublishBranch                          := "master",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(coreJVM, slf4j, slf4jBridge, jpl)
  )
  .settings(macroDefinitionSettings)
  .dependsOn(coreJVM, coreJS, slf4j, slf4jBridge, jpl)
  .enablePlugins(WebsitePlugin)

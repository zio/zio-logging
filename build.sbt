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

val ZioVersion      = "2.0.8"
val slf4jVersion    = "1.7.36"
val slf4j2Version   = "2.0.6"
val logbackVersion  = "1.2.11"
val logback2Version = "1.4.5"

addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check")

addCommandAlias(
  "testJVM8",
  ";coreJVM/test;slf4j/test;slf4jBridge/test"
)

addCommandAlias(
  "testJVM",
  ";coreJVM/test;slf4j/test;slf4j2/test;jpl/test;slf4jBridge/test;slf4j2Bridge/test"
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
  .aggregate(
    coreJVM,
    coreJS,
    slf4j,
    slf4j2,
    slf4jBridge,
    slf4j2Bridge,
    jpl,
    benchmarks,
    examplesCore,
    examplesJpl,
    examplesSlf4j2Bridge,
    examplesSlf4jLogback,
    examplesSlf4j2Logback,
    examplesSlf4j2Log4j,
    docs
  )

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

lazy val slf4j2 = project
  .in(file("slf4j2"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-slf4j2"))
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"               % "slf4j-api"                % slf4j2Version,
      "dev.zio"               %%% "zio-test"                 % ZioVersion      % Test,
      "dev.zio"               %%% "zio-test-sbt"             % ZioVersion      % Test,
      "ch.qos.logback"          % "logback-classic"          % logback2Version % Test,
      "net.logstash.logback"    % "logstash-logback-encoder" % "7.2"           % Test,
      "org.scala-lang.modules" %% "scala-collection-compat"  % "2.9.0"         % Test
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

lazy val slf4j2Bridge = project
  .in(file("slf4j2-bridge"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-slf4j2-bridge", "9"))
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    compileOrder            := CompileOrder.JavaThenScala,
    javacOptions            := jpmsOverwriteModulePath((Compile / dependencyClasspath).value.map(_.data))(javacOptions.value),
    javaOptions             := jpmsOverwriteModulePath((Compile / dependencyClasspath).value.map(_.data))(javaOptions.value),
    Compile / doc / sources := Seq.empty // module-info.java compilation issue
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"               % "slf4j-api"               % slf4j2Version,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.8.1",
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

lazy val examplesCore = project
  .in(file("examples/core"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-examples-core"))
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "dev.zio"  %% "zio-metrics-connectors" % "2.0.4",
      "dev.zio" %%% "zio-test"               % ZioVersion % Test,
      "dev.zio" %%% "zio-test-sbt"           % ZioVersion % Test
    )
  )

lazy val examplesSlf4jLogback = project
  .in(file("examples/slf4j-logback"))
  .dependsOn(slf4j)
  .settings(stdSettings("zio-logging-examples-slf4j-logback"))
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback"       % "logback-classic"          % logbackVersion,
      "net.logstash.logback" % "logstash-logback-encoder" % "6.6"
    )
  )

lazy val examplesSlf4j2Logback = project
  .in(file("examples/slf4j2-logback"))
  .dependsOn(slf4j2)
  .settings(stdSettings("zio-logging-examples-slf4j2-logback"))
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback"       % "logback-classic"          % logback2Version,
      "net.logstash.logback" % "logstash-logback-encoder" % "7.2"
    )
  )

lazy val examplesSlf4j2Log4j = project
  .in(file("examples/slf4j2-log4j"))
  .dependsOn(slf4j2)
  .settings(stdSettings("zio-logging-examples-slf4j2-log4j"))
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.apache.logging.log4j" % "log4j-slf4j2-impl" % "2.19.0",
      "org.apache.logging.log4j" % "log4j-core"        % "2.19.0"
    )
  )

lazy val examplesJpl = project
  .in(file("examples/jpl"))
  .dependsOn(jpl)
  .settings(stdSettings("zio-logging-examples-jpl"))
  .settings(
    publish / skip := true
  )

lazy val examplesSlf4j2Bridge = project
  .in(file("examples/slf4j2-bridge"))
  .dependsOn(slf4j2Bridge)
  .settings(stdSettings("zio-logging-examples-slf4j2-bridge"))
  .settings(
    publish / skip := true
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

import BuildHelper._
import MimaSettings.mimaSettings
import sbtcrossproject.CrossPlugin.autoImport.{ CrossType, crossProject }

enablePlugins(ZioSbtEcosystemPlugin, ZioSbtCiPlugin)

inThisBuild(
  List(
    name              := "zio-logging",
    ciEnabledBranches := Seq("master"),
    developers        := List(
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
  .settings(stdSettings("zio-logging", turnCompilerWarningIntoErrors = false))
  .settings(enableZIO(enableStreaming = true))
  .jvmSettings(
    Test / fork := true,
    run / fork  := true,
    mimaSettings(failOnProblem = true)
  )

lazy val coreJVM = core.jvm
lazy val coreJS  = core.js.settings(
  crossScalaVersions -= scala211.value,
  libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.4.0" % Test
)

lazy val slf4j = project
  .in(file("slf4j"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-slf4j", turnCompilerWarningIntoErrors = false))
  .settings(enableZIO())
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"               % "slf4j-api"                % slf4jVersion,
      "ch.qos.logback"          % "logback-classic"          % logbackVersion % Test,
      "net.logstash.logback"    % "logstash-logback-encoder" % "6.6"          % Test,
      "org.scala-lang.modules" %% "scala-collection-compat"  % "2.9.0"        % Test
    )
  )

lazy val slf4j2 = project
  .in(file("slf4j2"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-slf4j2", turnCompilerWarningIntoErrors = false))
  .settings(enableZIO())
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"               % "slf4j-api"                % slf4j2Version,
      "ch.qos.logback"          % "logback-classic"          % logback2Version % Test,
      "net.logstash.logback"    % "logstash-logback-encoder" % "7.3"           % Test,
      "org.scala-lang.modules" %% "scala-collection-compat"  % "2.9.0"         % Test
    )
  )

lazy val slf4jBridge = project
  .in(file("slf4j-bridge"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-slf4j-bridge", turnCompilerWarningIntoErrors = false))
  .settings(enableZIO())
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"               % "slf4j-api"               % slf4jVersion,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.9.0"
    )
  )

lazy val slf4j2Bridge = project
  .in(file("slf4j2-bridge"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-slf4j2-bridge", javaPlatform = "9", turnCompilerWarningIntoErrors = false))
  .settings(enableZIO())
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
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.8.1"
    )
  )
  .settings(enableZIO())

lazy val jpl = project
  .in(file("jpl"))
  .dependsOn(coreJVM)
  .settings(stdSettings("zio-logging-jpl", javaPlatform = "9", turnCompilerWarningIntoErrors = false))
  .settings(mimaSettings(failOnProblem = true))

lazy val benchmarks = project
  .in(file("benchmarks"))
  .settings(stdSettings("zio-logging-benchmarks", turnCompilerWarningIntoErrors = false))
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
  .settings(stdSettings("zio-logging-examples-core", turnCompilerWarningIntoErrors = false))
  .settings(enableZIO())
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-metrics-connectors" % "2.0.4"
    )
  )

lazy val examplesSlf4jLogback = project
  .in(file("examples/slf4j-logback"))
  .dependsOn(slf4j)
  .settings(stdSettings("zio-logging-examples-slf4j-logback", turnCompilerWarningIntoErrors = false))
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
  .settings(stdSettings("zio-logging-examples-slf4j2-logback", turnCompilerWarningIntoErrors = false))
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback"       % "logback-classic"          % logback2Version,
      "net.logstash.logback" % "logstash-logback-encoder" % "7.3"
    )
  )

lazy val examplesSlf4j2Log4j = project
  .in(file("examples/slf4j2-log4j"))
  .dependsOn(slf4j2)
  .settings(stdSettings("zio-logging-examples-slf4j2-log4j", turnCompilerWarningIntoErrors = false))
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.apache.logging.log4j" % "log4j-slf4j2-impl" % "2.20.0",
      "org.apache.logging.log4j" % "log4j-core"        % "2.20.0"
    )
  )

lazy val examplesJpl = project
  .in(file("examples/jpl"))
  .dependsOn(jpl)
  .settings(stdSettings("zio-logging-examples-jpl", turnCompilerWarningIntoErrors = false))
  .settings(
    publish / skip := true
  )

lazy val examplesSlf4j2Bridge = project
  .in(file("examples/slf4j2-bridge"))
  .dependsOn(slf4j2Bridge)
  .settings(stdSettings("zio-logging-examples-slf4j2-bridge", turnCompilerWarningIntoErrors = false))
  .settings(
    publish / skip := true
  )

lazy val docs = project
  .in(file("zio-logging-docs"))
  .settings(
    moduleName                                 := "zio-logging-docs",
    crossScalaVersions                         := Seq(scala213.value),
    projectName                                := (ThisBuild / name).value,
    mainModuleName                             := (coreJVM / name).value,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(coreJVM, slf4j, slf4jBridge, jpl),
    projectStage                               := ProjectStage.ProductionReady
  )
  .settings(macroDefinitionSettings)
  .dependsOn(coreJVM, coreJS, slf4j, slf4jBridge, jpl)
  .enablePlugins(WebsitePlugin)

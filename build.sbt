import BuildHelper.*
import Versions.*
import MimaSettings.mimaSettings
import sbtcrossproject.CrossPlugin.autoImport.{ CrossType, crossProject }
import zio.sbt.ZioSbtCiPlugin.{ CacheDependencies, Checkout, SetupJava, SetupLibuv }
import zio.sbt.githubactions.{ Job, Strategy }
import zio.sbt.githubactions.Step.SingleStep

enablePlugins(ZioSbtEcosystemPlugin, ZioSbtCiPlugin)

lazy val ciRunsOn = "ubuntu-22.04"

def ciJobWithSetup(job: Job) = job.copy(runsOn = ciRunsOn)

inThisBuild(
  List(
    name               := "zio-logging",
    ciEnabledBranches  := Seq("master"),
    // ciJvmOptions ++= Seq("-Xmx6g", "-Xss2m", "-XX:+UseG1GC"),
    ciTestJobs         := ciTestJobs.value.map(ciJobWithSetup) :+ compileExamplesJob.value,
    ciLintJobs         := ciLintJobs.value.map(ciJobWithSetup),
    ciBuildJobs        := ciBuildJobs.value.map(ciJobWithSetup),
    ciReleaseJobs      := ciReleaseJobs.value.map(ciJobWithSetup),
    ciUpdateReadmeJobs := ciUpdateReadmeJobs.value.map(ciJobWithSetup),
    ciPostReleaseJobs  := ciPostReleaseJobs.value.map(ciJobWithSetup),
    developers         := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net")),
      Developer(
        "pshemass",
        "Przemyslaw Wierzbicki",
        "rzbikson@gmail.com",
        url("https://github.com/pshemass")
      ),
      Developer("justcoon", "Peter Kotula", "peto.kotula@yahoo.com", url("https://github.com/justcoon"))
    ),
    zioVersion         := "2.1.23",
    scala213           := "2.13.18"
  )
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
    coreNative,
    slf4j,
    slf4j2,
    slf4jBridge,
    slf4j2Bridge,
    jpl,
    julBridge,
    benchmarks,
    examplesCoreJVM,
    examplesCoreJS,
    examplesCoreNative,
    examplesJpl,
    examplesJulBridge,
    examplesSlf4j2Bridge,
    examplesSlf4jLogback,
    examplesSlf4j2Logback,
    examplesSlf4j2Log4j,
    docs
  )

lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .settings(stdSettings(Some("zio-logging"), turnCompilerWarningIntoErrors = false))
  .settings(enableZIO(enableStreaming = true))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-parser"  % zioParser,
      "dev.zio" %%% "zio-prelude" % zioPrelude
    )
  )
  .jvmSettings(
    Test / fork := true,
    run / fork  := true,
    mimaSettings(failOnProblem = true)
  )

lazy val coreJVM    = core.jvm
lazy val coreJS     = core.js.settings(
  libraryDependencies ++= Seq(
    "io.github.cquiroz" %%% "scala-java-time"           % scalaJavaTimeVersion,
    "io.github.cquiroz" %%% "scala-java-time-tzdb"      % scalaJavaTimeVersion,
    ("org.scala-js"     %%% "scalajs-java-securerandom" % "1.0.0" % Test).cross(CrossVersion.for3Use2_13)
  )
)
lazy val coreNative = core.native.settings(
  libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion
)

lazy val slf4j = project
  .in(file("slf4j"))
  .dependsOn(coreJVM)
  .settings(stdSettings(Some("zio-logging-slf4j"), turnCompilerWarningIntoErrors = false))
  .settings(enableZIO())
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"               % "slf4j-api"                % slf4jVersion,
      "ch.qos.logback"          % "logback-classic"          % logbackVersion                % Test,
      "net.logstash.logback"    % "logstash-logback-encoder" % logstashLogbackEncoderVersion % Test,
      "org.scala-lang.modules" %% "scala-collection-compat"  % scalaCollectionCompatVersion  % Test
    )
  )

lazy val slf4j2 = project
  .in(file("slf4j2"))
  .dependsOn(coreJVM)
  .settings(stdSettings(Some("zio-logging-slf4j2"), turnCompilerWarningIntoErrors = false))
  .settings(enableZIO())
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"               % "slf4j-api"                % slf4j2Version,
      "ch.qos.logback"          % "logback-classic"          % logback2Version              % Test,
      "net.logstash.logback"    % "logstash-logback-encoder" % "9.0"                        % Test,
      "org.scala-lang.modules" %% "scala-collection-compat"  % scalaCollectionCompatVersion % Test
    )
  )

lazy val slf4jBridge = project
  .in(file("slf4j-bridge"))
  .dependsOn(coreJVM)
  .settings(stdSettings(Some("zio-logging-slf4j-bridge"), turnCompilerWarningIntoErrors = false))
  .settings(enableZIO())
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"               % "slf4j-api"               % slf4jVersion,
      "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionCompatVersion
    )
  )

lazy val slf4j2Bridge = project
  .in(file("slf4j2-bridge"))
  .dependsOn(coreJVM)
  .settings(stdSettings(Some("zio-logging-slf4j2-bridge"), turnCompilerWarningIntoErrors = false))
  .settings(enableZIO())
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    compileOrder            := CompileOrder.ScalaThenJava,
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

lazy val julBridge = project
  .in(file("jul-bridge"))
  .dependsOn(coreJVM)
  .settings(stdSettings(Some("zio-logging-jul-bridge"), turnCompilerWarningIntoErrors = false))
  .settings(enableZIO(enableTesting = true))
  .settings(mimaSettings(failOnProblem = true))
  .settings(
    Test / fork := true
  )

lazy val jpl = project
  .in(file("jpl"))
  .dependsOn(coreJVM)
  .settings(stdSettings(Some("zio-logging-jpl"), turnCompilerWarningIntoErrors = false))
  .settings(enableZIO(enableTesting = true))
  .settings(mimaSettings(failOnProblem = true))

lazy val benchmarks = project
  .in(file("benchmarks"))
  .settings(stdSettings(Some("zio-logging-benchmarks"), turnCompilerWarningIntoErrors = false))
  .settings(
    publish / skip := true,
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings"
  )
  .dependsOn(coreJVM)
  .enablePlugins(JmhPlugin)

lazy val examplesCore = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("examples/core"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(stdSettings(Some("zio-logging-examples-core"), turnCompilerWarningIntoErrors = false))
  .settings(enableZIO())
  .settings(
    publish / skip := true
  )

lazy val examplesCoreJVM    = examplesCore.jvm
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-metrics-connectors-prometheus" % zioMetricsConnectorsVersion,
      "dev.zio" %% "zio-http"                          % zioHttp,
      "dev.zio" %% "zio-config-typesafe"               % zioConfig
    )
  )
lazy val examplesCoreJS     = examplesCore.js
  .settings(
    scalaJSUseMainModuleInitializer := true
  )
lazy val examplesCoreNative = examplesCore.native

lazy val examplesSlf4jLogback = project
  .in(file("examples/slf4j-logback"))
  .dependsOn(slf4j)
  .settings(stdSettings(Some("zio-logging-examples-slf4j-logback"), turnCompilerWarningIntoErrors = false))
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback"       % "logback-classic"          % logbackVersion,
      "net.logstash.logback" % "logstash-logback-encoder" % logstashLogbackEncoderVersion
    )
  )

lazy val examplesSlf4j2Logback = project
  .in(file("examples/slf4j2-logback"))
  .dependsOn(slf4j2)
  .settings(stdSettings(Some("zio-logging-examples-slf4j2-logback"), turnCompilerWarningIntoErrors = false))
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback"       % "logback-classic"          % logback2Version,
      "net.logstash.logback" % "logstash-logback-encoder" % "9.0"
    )
  )

lazy val examplesSlf4j2Log4j = project
  .in(file("examples/slf4j2-log4j"))
  .dependsOn(slf4j2)
  .settings(stdSettings(Some("zio-logging-examples-slf4j2-log4j"), turnCompilerWarningIntoErrors = false))
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.apache.logging.log4j" % "log4j-slf4j2-impl" % log4jVersion,
      "org.apache.logging.log4j" % "log4j-core"        % log4jVersion
    )
  )

lazy val examplesJpl = project
  .in(file("examples/jpl"))
  .dependsOn(jpl)
  .settings(stdSettings(Some("zio-logging-examples-jpl"), turnCompilerWarningIntoErrors = false))
  .settings(
    publish / skip := true
  )

lazy val examplesJulBridge = project
  .in(file("examples/jul-bridge"))
  .dependsOn(julBridge)
  .settings(stdSettings(Some("zio-logging-examples-jul-bridge"), turnCompilerWarningIntoErrors = false))
  .settings(
    publish / skip := true
  )

lazy val examplesSlf4j2Bridge = project
  .in(file("examples/slf4j2-bridge"))
  .dependsOn(slf4j2Bridge)
  .settings(stdSettings(Some("zio-logging-examples-slf4j2-bridge"), turnCompilerWarningIntoErrors = false))
  .settings(
    publish / skip := true
  )

lazy val docs = project
  .in(file("zio-logging-docs"))
  .settings(
    moduleName                                 := "zio-logging-docs",
    projectName                                := (ThisBuild / name).value,
    mainModuleName                             := (coreJVM / name).value,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(coreJVM, slf4j, slf4jBridge, jpl),
    projectStage                               := ProjectStage.ProductionReady,
    publish / skip                             := false
  )
  .settings(macroDefinitionSettings)
  .dependsOn(coreJVM, coreJS, slf4j, slf4jBridge, jpl)
  .enablePlugins(WebsitePlugin)

lazy val compileExamplesJob = Def.setting {
  Job(
    id = "compile-examples",
    name = "Compile examples",
    steps = Seq(
      SetupLibuv,
      SetupJava(),
      CacheDependencies,
      Checkout.value,
      SingleStep(
        name = "Compile additional subprojects",
        run = Some(
          "sbt ++${{ matrix.scala }} examplesCoreJVM/compile examplesCoreJS/compile examplesCoreNative/compile examplesJpl/compile examplesJulBridge/compile " +
            "examplesSlf4j2Bridge/compile examplesSlf4jLogback/compile examplesSlf4j2Logback/compile " +
            "examplesSlf4j2Log4j/compile benchmarks/compile"
        )
      )
    ),
    strategy = Some(
      Strategy(
        matrix = Map(
          "scala" -> List(scala212.value, scala213.value, scala3.value)
        )
      )
    ),
    runsOn = ciRunsOn
  )
}

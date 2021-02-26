addSbtPlugin("ch.epfl.scala"    % "sbt-bloop"                 % "1.4.7")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"              % "2.4.2")
addSbtPlugin("org.scalameta"    % "sbt-mdoc"                  % "2.2.17")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.2.16")
addSbtPlugin("com.geirsson"     % "sbt-ci-release"            % "1.5.5")
addSbtPlugin("ch.epfl.lamp"     % "sbt-dotty"                 % "0.5.3")

// Scala-js support
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.5.0")

// Benchmark
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.0")

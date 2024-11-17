val zioSbtVersion = "0.0.0+527-f0ace4e7-SNAPSHOT"

addSbtPlugin("nl.thijsbroersen" % "zio-sbt-ecosystem" % zioSbtVersion)
addSbtPlugin("nl.thijsbroersen" % "zio-sbt-website"   % zioSbtVersion)
addSbtPlugin("nl.thijsbroersen" % "zio-sbt-ci"        % zioSbtVersion)

// Benchmarking Plugins
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.7")

resolvers ++= Resolver.sonatypeOssRepos("public")

val zioSbtVersion = "0.5.0"

addSbtPlugin("dev.zio"      % "zio-sbt-ecosystem" % zioSbtVersion)
addSbtPlugin("dev.zio"      % "zio-sbt-website"   % zioSbtVersion)
addSbtPlugin("dev.zio"      % "zio-sbt-ci"        % zioSbtVersion)
addSbtPlugin("com.typesafe" % "sbt-mima-plugin"   % "1.1.5")
addSbtPlugin("org.scala-js" % "sbt-scalajs"       % "1.20.2")

addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.10")

resolvers ++= Resolver.sonatypeOssRepos("public")

val zioSbtVersion = "0.4.0-alpha.30"

addSbtPlugin("dev.zio"      % "zio-sbt-ecosystem" % zioSbtVersion)
addSbtPlugin("dev.zio"      % "zio-sbt-website"   % zioSbtVersion)
addSbtPlugin("dev.zio"      % "zio-sbt-ci"        % zioSbtVersion)
addSbtPlugin("com.typesafe" % "sbt-mima-plugin"   % "1.1.4")
addSbtPlugin("org.scala-js" % "sbt-scalajs"       % "1.17.0")

addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.6")

resolvers ++= Resolver.sonatypeOssRepos("public")

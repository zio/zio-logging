val zioSbtVersion = "0.4.0-alpha.26"

addSbtPlugin("dev.zio"      % "zio-sbt-ecosystem" % zioSbtVersion)
addSbtPlugin("dev.zio"      % "zio-sbt-website"   % zioSbtVersion)
addSbtPlugin("dev.zio"      % "zio-sbt-ci"        % zioSbtVersion)
addSbtPlugin("com.typesafe" % "sbt-mima-plugin"   % "1.1.3")
addSbtPlugin("org.scala-js" % "sbt-scalajs"       % "1.16.0")

resolvers ++= Resolver.sonatypeOssRepos("public")

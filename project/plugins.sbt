addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.5.7")
addSbtPlugin("org.lyranthe.sbt" % "partial-unification" % "1.1.0")
addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.3.7")

// Database migrations
addSbtPlugin("org.flywaydb" % "flyway-sbt" % "4.2.0")

resolvers += "Flyway".at("https://davidmweber.github.io/flyway-sbt.repo")
// Native Packager allows us to create standalone jar
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.4")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")

addSbtPlugin("com.tapad" % "sbt-docker-compose" % "1.0.34")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")


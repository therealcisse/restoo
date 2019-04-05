addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.4")
addSbtPlugin("org.lyranthe.sbt" % "partial-unification" % "1.1.2")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.1")

// Database migrations
resolvers += "Flyway".at("https://davidmweber.github.io/flyway-sbt.repo")
addSbtPlugin("org.flywaydb" % "flyway-sbt" % "4.2.0")

// Native Packager allows us to create standalone jar
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.20")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.11")

addSbtPlugin("com.tapad" % "sbt-docker-compose" % "1.0.34")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.4.0")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")

addSbtPlugin("com.scalapenos" % "sbt-prompt" % "1.0.2")

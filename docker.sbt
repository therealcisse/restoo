maintainer in Docker := "Amadou Cisse <cisse.amadou.9@gmail.com>"
packageSummary in Docker := "Restaurant stock management"

dockerBaseImage := "openjdk:8-jre-alpine"

// Don't pull from dockerhub when building inside TRAVIS-CI
dockerRepository := (if (sys.env.get("TRAVIS").isEmpty) Some("amsayk") else None)

// TODO: revert once https://github.com/sbt/sbt-native-packager/issues/1202 is fixed
daemonUserUid in Docker := None
daemonUser in Docker := "daemon"

dockerUpdateLatest := true

enablePlugins(AshScriptPlugin)

dockerExposedPorts := Seq(8080)

dockerImageCreationTask := (publishLocal in Docker).value

variablesForSubstitution := Map("VERSION" -> version.value)

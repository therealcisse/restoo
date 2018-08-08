maintainer in Docker := "Amadou Cisse <cisse.amadou.9@gmail.com>"
packageSummary in Docker := "Restaurant stock management"

dockerBaseImage := "openjdk:8-jre-alpine"

version in Docker := "latest"

enablePlugins(AshScriptPlugin)

dockerExposedPorts := Seq(8080)

dockerImageCreationTask := (publishLocal in Docker).value

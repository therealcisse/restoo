organization    := "name.amadoucisse"
name            := "restoo"
version         := "0.0.1-SNAPSHOT"
scalaVersion    := "2.12.6"

val Http4sVersion     = "0.18.14"

val LogbackVersion    = "1.2.3"

libraryDependencies ++= Seq(
  "org.http4s"      %% "http4s-blaze-server" % Http4sVersion,
  "org.http4s"      %% "http4s-circe"        % Http4sVersion,
  "org.http4s"      %% "http4s-dsl"          % Http4sVersion,

  "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,
)

enablePlugins(ScalafmtPlugin, JavaAppPackaging)

cancelable in Global := true
fork in run := true

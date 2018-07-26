organization    := "name.amadoucisse"
name            := "restoo"
version         := "0.0.1-SNAPSHOT"
scalaVersion    := "2.12.6"

val CatsVersion       = "1.1.0"
val CirceVersion      = "0.9.3"
val Http4sVersion     = "0.18.14"
val DoobieVersion     = "0.5.3"
val H2Version         = "1.4.196"
val FlywayVersion     = "4.2.0"
val PureConfigVersion = "0.9.1"

val LogbackVersion    = "1.2.3"

libraryDependencies ++= Seq(
  "org.typelevel"         %% "cats-core"            % CatsVersion,

  "io.circe"              %% "circe-generic"        % CirceVersion,
  "io.circe"              %% "circe-literal"        % CirceVersion,
  "io.circe"              %% "circe-generic-extras" % CirceVersion,
  "io.circe"              %% "circe-optics"         % CirceVersion,
  "io.circe"              %% "circe-parser"         % CirceVersion,
  "io.circe"              %% "circe-java8"          % CirceVersion,

  "org.tpolecat"          %% "doobie-core"          % DoobieVersion,
  "org.tpolecat"          %% "doobie-h2"            % DoobieVersion,
  "org.tpolecat"          %% "doobie-scalatest"     % DoobieVersion,
  "org.tpolecat"          %% "doobie-hikari"        % DoobieVersion,

  "org.http4s"      %% "http4s-blaze-server" % Http4sVersion,
  "org.http4s"      %% "http4s-circe"        % Http4sVersion,
  "org.http4s"      %% "http4s-dsl"          % Http4sVersion,

  "ch.qos.logback"  %  "logback-classic"     % LogbackVersion,

  "org.flywaydb"          %  "flyway-core"  % FlywayVersion,
  "com.github.pureconfig" %% "pureconfig"   % PureConfigVersion,

)

enablePlugins(ScalafmtPlugin, JavaAppPackaging)

cancelable in Global := true
fork in run := true

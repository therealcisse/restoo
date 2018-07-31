organization    := "name.amadoucisse"
name            := "restoo"
version         := "0.1.0"
scalaVersion    := "2.12.6"

val CatsVersion       = "1.1.0"
val CirceVersion      = "0.9.3"
val Http4sVersion     = "0.18.15"
val ScalaCheckVersion = "1.14.0"
val ScalaTestVersion  = "3.0.4"
val DoobieVersion     = "0.5.3"
val H2Version         = "1.4.196"
val FlywayVersion     = "5.1.4"
val PureConfigVersion = "0.9.1"

val LogbackVersion    = "1.2.3"

val SwaggerUIVersion  = "3.9.3"

val OpencensusHtt4sVersion   = "0.6.0"
val OpencensusLoggingVersion = "0.15.0"
val OpencensusZipkinVersion  = "0.15.0"

libraryDependencies ++= Seq(
  "org.typelevel"           %% "cats-core"              % CatsVersion,

  "io.circe"                %% "circe-generic"          % CirceVersion,
  "io.circe"                %% "circe-literal"          % CirceVersion,
  "io.circe"                %% "circe-generic-extras"   % CirceVersion,
  "io.circe"                %% "circe-optics"           % CirceVersion,
  "io.circe"                %% "circe-parser"           % CirceVersion,
  "io.circe"                %% "circe-java8"            % CirceVersion,

  "org.tpolecat"            %% "doobie-core"            % DoobieVersion,
  "org.tpolecat"            %% "doobie-h2"              % DoobieVersion,
  "org.tpolecat"            %% "doobie-scalatest"       % DoobieVersion,
  "org.tpolecat"            %% "doobie-hikari"          % DoobieVersion,

  "org.http4s"              %% "http4s-blaze-server"    % Http4sVersion,
  "org.http4s"              %% "http4s-circe"           % Http4sVersion,
  "org.http4s"              %% "http4s-dsl"             % Http4sVersion,

  "org.http4s"              %% "http4s-prometheus-server-metrics" % Http4sVersion,

  "org.scalacheck"          %% "scalacheck"             % ScalaCheckVersion % Test,
  "org.scalatest"           %% "scalatest"              % ScalaTestVersion  % Test,

  "ch.qos.logback"          %  "logback-classic"        % LogbackVersion,

  "org.flywaydb"            %  "flyway-core"            % FlywayVersion,
  "com.github.pureconfig"   %% "pureconfig"             % PureConfigVersion,

  "org.webjars"             % "swagger-ui"              % SwaggerUIVersion,

  "com.github.sebruck"      %% "opencensus-scala-http4s"              % OpencensusHtt4sVersion,
  "io.opencensus"           % "opencensus-exporter-trace-logging"     % OpencensusLoggingVersion,
  "io.opencensus"           % "opencensus-exporter-trace-zipkin"      % OpencensusZipkinVersion,
)

enablePlugins(ScalafmtPlugin, JavaAppPackaging)

cancelable in Global := true
fork in run := true

maxErrors := 5
triggeredMessage := Watched.clearWhenTriggered

scalafmtOnCompile := true

import ReleaseTransformations._
import java.io.File
import ScalafmtPlugin._

organization    := "name.amadoucisse"
name            := "restoo"
scalaVersion    := "2.12.8"

val CatsVersion         = "1.5.0"
val CirceVersion        = "0.11.0"
val CirceJava8Version   = "0.10.1"
val CirceOpticsVersion  = "0.10.0"
val MeowMTLVersion      = "0.2.0"
val Http4sVersion       = "0.20.0-M5"
val ScalaCheckVersion   = "1.14.0"
val ScalaTestVersion    = "3.0.5"
val DoobieVersion       = "0.6.0"
val H2Version           = "1.4.196"
val FlywayVersion       = "5.2.4"
val PureConfigVersion   = "0.10.1"

val LogbackVersion    = "1.2.3"

val SwaggerUIVersion  = "3.20.5"

//val OpencensusHttp4sVersion   = "0.6.0"
//val OpencensusLoggingVersion = "0.15.1"
//val OpencensusZipkinVersion  = "0.15.1"

val ScribeVersion = "2.7.1"

val RefinedVersion = "0.9.4"

libraryDependencies ++= Seq(
  "org.typelevel"           %% "cats-core"              % CatsVersion,

  "com.olegpy"              %% "meow-mtl"               % MeowMTLVersion,

  "io.circe"                %% "circe-core"             % CirceVersion,
  "io.circe"                %% "circe-literal"          % CirceVersion,
  "io.circe"                %% "circe-generic-extras"   % CirceVersion,
  "io.circe"                %% "circe-optics"           % CirceOpticsVersion,
  "io.circe"                %% "circe-parser"           % CirceVersion,
  "io.circe"                %% "circe-java8"            % CirceJava8Version,

  "org.tpolecat"            %% "doobie-core"            % DoobieVersion,
  "org.tpolecat"            %% "doobie-h2"              % DoobieVersion,
  "org.tpolecat"            %% "doobie-scalatest"       % DoobieVersion,
  "org.tpolecat"            %% "doobie-hikari"          % DoobieVersion,
  "org.tpolecat"            %% "doobie-postgres"        % DoobieVersion,

  "org.http4s"              %% "http4s-blaze-server"    % Http4sVersion,
  "org.http4s"              %% "http4s-circe"           % Http4sVersion,
  "org.http4s"              %% "http4s-dsl"             % Http4sVersion,
  "org.http4s"              %% "http4s-blaze-client"    % Http4sVersion,

  "org.http4s"              %% "http4s-prometheus-metrics" % Http4sVersion,

  "org.scalacheck"          %% "scalacheck"             % ScalaCheckVersion % Test,
  "org.scalatest"           %% "scalatest"              % ScalaTestVersion  % Test,

  "ch.qos.logback"          %  "logback-classic"        % LogbackVersion,

  "org.flywaydb"            %  "flyway-core"            % FlywayVersion,
  "com.github.pureconfig"   %% "pureconfig"             % PureConfigVersion,

  "org.webjars"             % "swagger-ui"              % SwaggerUIVersion,

//  "com.github.sebruck"      %% "opencensus-scala-http4s"              % OpencensusHttp4sVersion,
//  "io.opencensus"           % "opencensus-exporter-trace-logging"     % OpencensusLoggingVersion,
//  "io.opencensus"           % "opencensus-exporter-trace-zipkin"      % OpencensusZipkinVersion,

  "eu.timepit"              %% "refined"                              % RefinedVersion,
  "eu.timepit"              %% "refined-scalacheck"                   % RefinedVersion,
  "eu.timepit"              %% "refined-pureconfig"                   % RefinedVersion,

  "com.outr"                %% "scribe"                               % ScribeVersion,
)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.9")
addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.3.0-M4")

enablePlugins(ScalafmtPlugin, JavaAppPackaging, DockerComposePlugin, BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey]("SwaggerUIVersion" -> SwaggerUIVersion)
buildInfoPackage := organization.value + "." + name.value
buildInfoObject := "Info"

Defaults.itSettings

inConfig(IntegrationTest)(scalafmtConfigSettings)

lazy val root = project.in(file(".")).configs(IntegrationTest)

//To use 'dockerComposeTest' to run tests in the 'IntegrationTest' scope instead of the default 'Test' scope:
// 1) Package the tests that exist in the IntegrationTest scope
testCasesPackageTask := (sbt.Keys.packageBin in IntegrationTest).value
// 2) Specify the path to the IntegrationTest jar produced in Step 1
testCasesJar := artifactPath.in(IntegrationTest, packageBin).value.getAbsolutePath
// 3) Include any IntegrationTest scoped resources on the classpath if they are used in the tests
testDependenciesClasspath := {
  val fullClasspathCompile   = (fullClasspath in Compile).value
  val classpathTestManaged   = (managedClasspath in IntegrationTest).value
  val classpathTestUnmanaged = (unmanagedClasspath in IntegrationTest).value
  val testResources          = (resources in IntegrationTest).value
  (fullClasspathCompile.files ++ classpathTestManaged.files ++ classpathTestUnmanaged.files ++ testResources)
    .map(_.getAbsoluteFile)
    .mkString(File.pathSeparator)
}

cancelable in Global := true
fork in run := true

maxErrors := 5
triggeredMessage := Watched.clearWhenTriggered

scalafmtOnCompile := true

skip in publish := true

releaseProcess := Seq[ReleaseStep](
  inquireVersions,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("publish"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)


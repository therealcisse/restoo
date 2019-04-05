addCompilerPlugin(scalafixSemanticdb)
scalacOptions += "-Yrangepos"

addCommandAlias("scalafixAll", "all compile:scalafix test:scalafix")
addCommandAlias("scalafixCheck", "; scalafix --check ; test:scalafix --check")

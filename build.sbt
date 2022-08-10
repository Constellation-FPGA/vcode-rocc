lazy val commonSettings = Seq(
  organization := "edu.northwestern.eecs",
  version := "0.0",
  name := "vcode-rocc",
  // Chosen to be the same version as rocket-chip
  scalaVersion := "2.12.15",
)

val chiselVersion = "3.5.2"
lazy val chiselSettings = Seq(
  libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel3" % chiselVersion),
  addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
)

val rocketChipVersion = "1.2.6"
lazy val rocketChipSettings = Seq(
  libraryDependencies ++= Seq("edu.berkeley.cs" %% "rocketchip" % rocketChipVersion)
)

lazy val `vcode-rocc` = (project in file("."))
  .settings(chiselSettings, rocketChipSettings)

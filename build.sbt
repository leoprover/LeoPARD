def commonSettings = Seq(
  organization := "leo",
  version := "0.3",
  scalaVersion := "2.11.6",
  name := "leo",
  sourcesInBase := false,
  scalaSource in Compile := baseDirectory.value / "src/main",
  resourceDirectory in Compile := baseDirectory.value / "src/main/resources",
  unmanagedJars in Compile := Seq.empty
)

lazy val leo = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.3",
      "org.scala-lang.modules" %% "scala-xml" % "1.0.3")
  )

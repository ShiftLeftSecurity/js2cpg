val cpgVersion   = "1.4.10"
val joernVersion = "2.0.44"

val gitCommitString = SettingKey[String]("gitSha")

enablePlugins(JavaAppPackaging, BuildInfoPlugin)

lazy val Fast = config("fast").extend(Test)
configs(Fast)
inConfig(Fast)(Defaults.testTasks)
// run fast:test to exclude all tests tagged with @Slow:
Fast / testOptions += Tests.Argument("-l", "org.scalatest.tags.Slow")

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / excludeLintKeys += Fast / configuration
Global / excludeLintKeys += gitCommitString

lazy val commonSettings = Seq(
  scalaVersion := "3.3.0",
  organization := "io.shiftleft",
  scalacOptions ++= Seq("-Xtarget:8"),
  resolvers ++= Seq(
    Resolver.mavenLocal,
    "Atlassian Maven Repository" at "https://maven.atlassian.com/repository/public"
  ),
  libraryDependencies ++= Seq(
    "io.shiftleft"              %% "codepropertygraph" % cpgVersion,
    "io.joern"                  %% "x2cpg"             % joernVersion,
    "com.github.scopt"          %% "scopt"             % "4.1.0",
    "org.graalvm.js"             % "js"                % "22.3.3",
    "com.fasterxml.jackson.core" % "jackson-databind"  % "2.15.2",
    "com.atlassian.sourcemap"    % "sourcemap"         % "2.0.0",
    "commons-io"                 % "commons-io"        % "2.13.0",
    "org.slf4j"                  % "slf4j-api"         % "2.0.7",
    "org.apache.logging.log4j"   % "log4j-slf4j2-impl" % "2.21.1"     % Optional,
    "org.apache.logging.log4j"   % "log4j-core"        % "2.21.1"     % Optional,
    "io.joern"                  %% "x2cpg"             % joernVersion % Test classifier "tests",
    "org.scalatest"             %% "scalatest"         % "3.2.16"     % Test
  )
)

lazy val js2cpg = (project in file(".")).settings(
  commonSettings,
  name := "js2cpg",
  Test / unmanagedResources ++= Seq(
    baseDirectory.value / "src" / "test" / "resources" / "privatemodules" / ".npmrc",
    baseDirectory.value / "src" / "test" / "resources" / "ignoreprivatemodules" / ".npmrc",
    baseDirectory.value / "src" / "test" / "resources" / "enginecheck" / ".npmrc"
  ),
  Test / javaOptions ++= Seq("-Dlog4j.configurationFile=file:src/test/resources/log4j2-test.xml"),
  publishTo             := sonatypePublishToBundle.value,
  sonatypeTimeoutMillis := 7200000,
  scmInfo := Some(
    ScmInfo(url("https://github.com/ShiftLeftSecurity/js2cpg"), "scm:git@github.com:ShiftLeftSecurity/js2cpg.git")
  ),
  homepage := Some(url("https://github.com/ShiftLeftSecurity/js2cpg/")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer("max-leuthaeuser", "Max Leuthäuser", "max@shiftleft.io", url("https://github.com/max-leuthaeuser"))
  ),
  publishMavenStyle := true,
  gitCommitString   := git.gitHeadCommit.value.getOrElse("n/a"),
  buildInfoKeys     := Seq[BuildInfoKey](version, gitCommitString),
  buildInfoPackage  := "io.shiftleft.js2cpg.core"
)

Universal / packageName       := name.value
Universal / topLevelDirectory := None

val cpgVersion   = "1.3.477"
val joernVersion = "1.1.407"

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
  scalaVersion := "2.13.7",
  organization := "io.shiftleft",
  scalacOptions ++= Seq(
    // Emit warning and location for usages of deprecated APIs.
    "-deprecation",
    "-encoding",
    // Specify character encoding used by source files:
    "utf-8",
    // Explain type errors in more detail:
    "-explaintypes",
    // Emit warning and location for usages of features that should be imported explicitly:
    "-feature",
    // Allow higher-kinded types:
    "-language:higherKinds",
    // Allow definition of implicit functions called views:
    "-language:implicitConversions",
    // Enable additional warnings where generated code depends on assumptions:
    "-unchecked",
    // Wrap field accessors to throw an exception on uninitialized access:
    "-Xcheckinit",
    // Fail the compilation if there are any warnings:
    "-Xfatal-warnings",
    // Warn if an argument list is modified to match the receiver:
    "-Xlint:adapted-args",
    // Evaluation of a constant arithmetic expression results in an error:
    "-Xlint:constant",
    // Selecting member of DelayedInit:
    "-Xlint:delayedinit-select",
    // A Scaladoc comment appears to be detached from its element:
    "-Xlint:doc-detached",
    // Warn about inaccessible types in method signatures:
    "-Xlint:inaccessible",
    // Warn when a type argument is inferred to be `Any`:
    "-Xlint:infer-any",
    // A string literal appears to be missing an interpolator id:
    "-Xlint:missing-interpolator",
    // Option.apply used implicit view:
    "-Xlint:option-implicit",
    // Class or object defined in package object:
    "-Xlint:package-object-classes",
    // Parameterized overloaded implicit methods are not visible as view bounds:
    "-Xlint:poly-implicit-overload",
    // A private field (or class parameter) shadows a superclass field:
    "-Xlint:private-shadow",
    // Pattern sequence wildcard must align with sequence component:
    "-Xlint:stars-align",
    // A local type parameter shadows a type already in scope:
    "-Xlint:type-parameter-shadow",
    // Warn when dead code is identified:
    "-Ywarn-dead-code",
    // Warn when more than one implicit parameter section is defined:
    "-Ywarn-extra-implicit",
    // Warn when nullary methods return Unit:
    "-Xlint:nullary-unit",
    // Warn when numerics are widened:
    "-Ywarn-numeric-widen",
    // Warn if an implicit parameter is unused:
    "-Ywarn-unused:implicits"
  ),
  resolvers ++= Seq(
    Resolver.mavenLocal,
    "Atlassian Maven Repository" at "https://maven.atlassian.com/repository/public"
  ),
  libraryDependencies ++= Seq(
    "io.shiftleft"             %% "codepropertygraph" % cpgVersion,
    "io.shiftleft"             %% "semanticcpg"       % cpgVersion,
    "io.joern"                 %% "dataflowengineoss" % joernVersion,
    "com.github.scopt"         %% "scopt"             % "4.0.1",
    "org.graalvm.js"           % "js"                 % "21.3.0",
    "com.github.pathikrit"     %% "better-files"      % "3.9.1",
    "org.slf4j"                % "slf4j-api"          % "1.7.32",
    "org.apache.logging.log4j" % "log4j-slf4j-impl"   % "2.17.0" % Runtime,
    "com.typesafe.play"        %% "play-json"         % "2.9.2",
    "com.fasterxml.jackson"    % "jackson-base"       % "2.13.1",
    "com.atlassian.sourcemap"  % "sourcemap"          % "2.0.0",
    "commons-io"               % "commons-io"         % "2.11.0",
    "io.shiftleft"             %% "semanticcpg"       % cpgVersion % Test classifier "tests",
    "org.scalatest"            %% "scalatest"         % "3.2.10" % Test
  ),
  Test / fork := true
)

lazy val js2cpg = (project in file(".")).settings(
  commonSettings,
  name := "js2cpg",
  Test / unmanagedResources += baseDirectory.value / "src" / "test" / "resources" / "privatemodules" / ".npmrc",
  Test / unmanagedResources += baseDirectory.value / "src" / "test" / "resources" / "ignoreprivatemodules" / ".npmrc",
  Test / javaOptions ++= Seq(
    "-Dlog4j.configurationFile=file:src/test/resources/log4j2-test.xml"
  ),
  publishTo := sonatypePublishToBundle.value,
  sonatypeTimeoutMillis := 7200000,
  scmInfo := Some(
    ScmInfo(url("https://github.com/ShiftLeftSecurity/js2cpg"),
            "scm:git@github.com:ShiftLeftSecurity/js2cpg.git")),
  homepage := Some(url("https://github.com/ShiftLeftSecurity/js2cpg/")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer("max-leuthaeuser",
              "Max Leuthäuser",
              "max@shiftleft.io",
              url("https://github.com/max-leuthaeuser"))
  ),
  publishMavenStyle := true,
  gitCommitString := git.gitHeadCommit.value.getOrElse("n/a"),
  buildInfoKeys := Seq[BuildInfoKey](version, gitCommitString),
  buildInfoPackage := "io.shiftleft.js2cpg.core"
)

Universal / packageName := name.value
Universal / topLevelDirectory := None

package io.shiftleft.js2cpg.preprocessing

import io.shiftleft.js2cpg.core.Config

import java.nio.file.Path

trait Transpiler extends TranspilingEnvironment {

  protected val NODE_OPTIONS: Map[String, String] = Map(
    "NODE_OPTIONS" -> s"--max_old_space_size=${TranspilingEnvironment.maxMemoryParameter}"
  )

  protected val DEFAULT_IGNORED_DIRS: List[String] = List(
    "build",
    "dist",
    "vendor",
    "docs",
    "swagger",
    "cypress",
    ".yarn",
    "jspm_packages",
    "bower_components",
    "examples"
  )

  protected val DEFAULT_IGNORED_TEST_DIRS: List[String] = List(
    "test",
    "test_integration",
    "test-integration",
    "tests",
    "tests_integration",
    "tests-integration",
    "e2e",
    "mocks"
  )

  protected val config: Config
  protected val projectPath: Path

  def shouldRun(): Boolean

  def validEnvironment(): Boolean

  protected def logExecution(): Unit

  /** Runs the transpiler at the specified transpile directory tmpTranspileDir.
    *
    * @param tmpTranspileDir
    *   the directory to run the transpiler in
    * @return
    *   true if other transpilers in the chain (see [[TranspilerGroup]]) should run, false if not
    */
  protected def transpile(tmpTranspileDir: Path): Boolean

  def run(tmpTranspileDir: Path): Boolean =
    if (shouldRun() && validEnvironment()) {
      logExecution()
      transpile(tmpTranspileDir)
    } else true

}

package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.VUE_SUFFIX
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.js2cpg.parser.PackageJsonParser

import java.nio.file.Path

trait Transpiler {

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

  private def hasVueFiles: Boolean =
    FileUtils.getFileTree(projectPath, config, List(VUE_SUFFIX)).nonEmpty

  protected def isVueProject: Boolean = {
    val hasVueDep =
      new PackageJsonParser((File(projectPath) / PackageJsonParser.PACKAGE_JSON_FILENAME).path)
        .dependencies()
        .contains("vue")
    hasVueDep || hasVueFiles
  }

  def shouldRun(): Boolean

  def validEnvironment(): Boolean

  protected def logExecution(): Unit

  /**
    * Runs the transpiler at the specified transpile directory tmpTranspileDir.
    *
    * @param tmpTranspileDir the directory to run the transpiler in
    * @return true if other transpilers in the chain (see [[TranspilerGroup]]) should run, false if not
    */
  protected def transpile(tmpTranspileDir: Path): Boolean

  def run(tmpTranspileDir: Path): Boolean =
    if (shouldRun() && validEnvironment()) {
      logExecution()
      transpile(tmpTranspileDir)
    } else true

}

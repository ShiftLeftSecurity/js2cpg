package io.shiftleft.js2cpg.core

import io.shiftleft.js2cpg.io.FileDefaults.VSIX_SUFFIX

import java.io.File
import java.nio.file.{Path, Paths}
import io.shiftleft.js2cpg.parser.PackageJsonParser
import io.shiftleft.js2cpg.preprocessing.TypescriptTranspiler
import io.shiftleft.utils.IOUtils

import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex

object Config {

  val SL_IGNORE_FILE: String                            = ".slignore"
  val DEFAULT_TS_TYPES: Boolean                         = false
  val DEFAULT_TS_TRANSPILING: Boolean                   = true
  val DEFAULT_BABEL_TRANSPILING: Boolean                = true
  val DEFAULT_VUE_TRANSPILING: Boolean                  = true
  val DEFAULT_NUXT_TRANSPILING: Boolean                 = true
  val DEFAULT_TEMPLATE_TRANSPILING: Boolean             = true
  val DEFAULT_CPG_OUT_FILE: String                      = "cpg.bin.zip"
  val DEFAULT_IGNORED_FILES_REGEX: Regex                = "".r
  val DEFAULT_IGNORED_FILES: Seq[Path]                  = Seq.empty
  val DEFAULT_IGNORE_MINIFIED: Boolean                  = true
  val DEFAULT_IGNORE_TESTS: Boolean                     = true
  val DEFAULT_IGNORE_PRIVATE_DEPS: Boolean              = false
  val DEFAULT_PRIVATE_DEPS: Seq[String]                 = Seq.empty
  val DEFAULT_INCLUDE_CONFIGS: Boolean                  = true
  val DEFAULT_INCLUDE_HTML: Boolean                     = true
  val DEFAULT_JVM_METRICS: Option[Int]                  = None
  val DEFAULT_MODULE_MODE: Option[String]               = None
  val DEFAULT_WITH_NODE_MODULES_FOLDER: Boolean         = false
  val DEFAULT_OPTIMIZE_DEPENDENCIES: Boolean            = true
  val DEFAULT_FIXED_TRANSPILATION_DEPENDENCIES: Boolean = false

}

case class Config(
  srcDir: String = "",
  tsTranspiling: Boolean = Config.DEFAULT_TS_TRANSPILING,
  babelTranspiling: Boolean = Config.DEFAULT_BABEL_TRANSPILING,
  vueTranspiling: Boolean = Config.DEFAULT_VUE_TRANSPILING,
  nuxtTranspiling: Boolean = Config.DEFAULT_NUXT_TRANSPILING,
  templateTranspiling: Boolean = Config.DEFAULT_TEMPLATE_TRANSPILING,
  packageJsonLocation: String = PackageJsonParser.PACKAGE_JSON_FILENAME,
  outputFile: String = Config.DEFAULT_CPG_OUT_FILE,
  withTsTypes: Boolean = Config.DEFAULT_TS_TYPES,
  ignoredFilesRegex: Regex = Config.DEFAULT_IGNORED_FILES_REGEX,
  ignoredFiles: Seq[Path] = Config.DEFAULT_IGNORED_FILES,
  ignoreMinified: Boolean = Config.DEFAULT_IGNORE_MINIFIED,
  ignoreTests: Boolean = Config.DEFAULT_IGNORE_TESTS,
  ignorePrivateDeps: Boolean = Config.DEFAULT_IGNORE_PRIVATE_DEPS,
  privateDeps: Seq[String] = Config.DEFAULT_PRIVATE_DEPS,
  includeConfigs: Boolean = Config.DEFAULT_INCLUDE_CONFIGS,
  includeHtml: Boolean = Config.DEFAULT_INCLUDE_HTML,
  jvmMetrics: Option[Int] = Config.DEFAULT_JVM_METRICS,
  moduleMode: Option[String] = Config.DEFAULT_MODULE_MODE,
  withNodeModuleFolder: Boolean = Config.DEFAULT_WITH_NODE_MODULES_FOLDER,
  optimizeDependencies: Boolean = Config.DEFAULT_OPTIMIZE_DEPENDENCIES,
  fixedTranspilationDependencies: Boolean = Config.DEFAULT_FIXED_TRANSPILATION_DEPENDENCIES
) {

  def createPathForPackageJson(): Path = Paths.get(packageJsonLocation) match {
    case path if path.isAbsolute => path
    case _ if srcDir.endsWith(VSIX_SUFFIX) =>
      Paths.get(srcDir, "extension", packageJsonLocation).toAbsolutePath.normalize()
    case _ => Paths.get(srcDir, packageJsonLocation).toAbsolutePath.normalize()
  }

  def createPathForIgnore(ignore: String): Path = {
    val path = Paths.get(ignore)
    if (path.isAbsolute) {
      path
    } else {
      Paths.get(srcDir, ignore).toAbsolutePath.normalize()
    }
  }

  def withLoadedIgnores(): Config = {
    val slIngoreFilePath = Paths.get(srcDir, Config.SL_IGNORE_FILE)
    Try(IOUtils.readLinesInFile(slIngoreFilePath)) match {
      case Failure(_) => this
      case Success(lines) =>
        this.copy(ignoredFiles = ignoredFiles ++ lines.map(createPathForIgnore))
    }
  }

  override def toString: String =
    s"""
      |\t- Source project: '$srcDir'
      |\t- package.json location: '${createPathForPackageJson()}'
      |\t- Module mode: '${moduleMode.getOrElse(TypescriptTranspiler.DEFAULT_MODULE)}'
      |\t- Optimize dependencies: $optimizeDependencies
      |\t- Fixed transpilations dependencies: $fixedTranspilationDependencies
      |\t- Typescript transpiling: $tsTranspiling
      |\t- Babel transpiling: $babelTranspiling
      |\t- Vue.js transpiling: $vueTranspiling
      |\t- Nuxt.js transpiling: $nuxtTranspiling
      |\t- Template transpiling: $templateTranspiling
      |\t- Ignored files regex: '$ignoredFilesRegex'
      |\t- Ignored folders: ${ignoredFiles
        .filter(f => new File(f.toString).isDirectory)
        .map(f => s"${System.lineSeparator()}\t\t'${f.toString}'")
        .mkString}
      |\t- Ignore minified files: $ignoreMinified
      |\t- Ignore test files: $ignoreTests
      |\t- Ignore private dependencies: $ignorePrivateDeps
      |\t- Additional private dependencies: ${privateDeps
        .map(f => s"${System.lineSeparator()}\t\t'$f'")
        .mkString}
      |\t- Include configuration files: $includeConfigs
      |\t- Include HTML files: $includeHtml
      |\t- Output file: '$outputFile'
      |""".stripMargin
}

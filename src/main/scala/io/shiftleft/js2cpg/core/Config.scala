package io.shiftleft.js2cpg.core

import io.joern.x2cpg.X2CpgConfig
import io.shiftleft.js2cpg.io.FileDefaults
import io.shiftleft.js2cpg.io.FileDefaults.VSIX_SUFFIX

import java.io.File
import java.nio.file.{Path, Paths}
import io.shiftleft.js2cpg.preprocessing.TypescriptTranspiler
import io.shiftleft.utils.IOUtils

import scala.util.{Failure, Success, Try}

object Config {
  val SL_IGNORE_FILE: String                            = ".slignore"
  val DEFAULT_TS_TYPES: Boolean                         = false
  val DEFAULT_TS_TRANSPILING: Boolean                   = true
  val DEFAULT_BABEL_TRANSPILING: Boolean                = true
  val DEFAULT_VUE_TRANSPILING: Boolean                  = true
  val DEFAULT_NUXT_TRANSPILING: Boolean                 = true
  val DEFAULT_TEMPLATE_TRANSPILING: Boolean             = true
  val DEFAULT_IGNORE_MINIFIED: Boolean                  = true
  val DEFAULT_IGNORE_TESTS: Boolean                     = true
  val DEFAULT_IGNORE_PRIVATE_DEPS: Boolean              = false
  val DEFAULT_PRIVATE_DEPS: Seq[String]                 = Seq.empty
  val DEFAULT_INCLUDE_HTML: Boolean                     = true
  val DEFAULT_JVM_METRICS: Option[Int]                  = None
  val DEFAULT_MODULE_MODE: Option[String]               = None
  val DEFAULT_WITH_NODE_MODULES_FOLDER: Boolean         = false
  val DEFAULT_OPTIMIZE_DEPENDENCIES: Boolean            = true
  val DEFAULT_FIXED_TRANSPILATION_DEPENDENCIES: Boolean = false

}

final case class Config(
  tsTranspiling: Boolean = Config.DEFAULT_TS_TRANSPILING,
  babelTranspiling: Boolean = Config.DEFAULT_BABEL_TRANSPILING,
  vueTranspiling: Boolean = Config.DEFAULT_VUE_TRANSPILING,
  nuxtTranspiling: Boolean = Config.DEFAULT_NUXT_TRANSPILING,
  templateTranspiling: Boolean = Config.DEFAULT_TEMPLATE_TRANSPILING,
  packageJsonLocation: String = FileDefaults.PACKAGE_JSON_FILENAME,
  withTsTypes: Boolean = Config.DEFAULT_TS_TYPES,
  ignoreMinified: Boolean = Config.DEFAULT_IGNORE_MINIFIED,
  ignoreTests: Boolean = Config.DEFAULT_IGNORE_TESTS,
  ignorePrivateDeps: Boolean = Config.DEFAULT_IGNORE_PRIVATE_DEPS,
  privateDeps: Seq[String] = Config.DEFAULT_PRIVATE_DEPS,
  includeHtml: Boolean = Config.DEFAULT_INCLUDE_HTML,
  jvmMetrics: Option[Int] = Config.DEFAULT_JVM_METRICS,
  moduleMode: Option[String] = Config.DEFAULT_MODULE_MODE,
  withNodeModuleFolder: Boolean = Config.DEFAULT_WITH_NODE_MODULES_FOLDER,
  optimizeDependencies: Boolean = Config.DEFAULT_OPTIMIZE_DEPENDENCIES,
  fixedTranspilationDependencies: Boolean = Config.DEFAULT_FIXED_TRANSPILATION_DEPENDENCIES
) extends X2CpgConfig[Config] {

  def createPathForPackageJson(): Path = Paths.get(packageJsonLocation) match {
    case path if path.isAbsolute => path
    case _ if inputPath.endsWith(VSIX_SUFFIX) =>
      Paths.get(inputPath, "extension", packageJsonLocation).toAbsolutePath.normalize()
    case _ => Paths.get(inputPath, packageJsonLocation).toAbsolutePath.normalize()
  }

  def withTsTranspiling(value: Boolean): Config = {
    copy(tsTranspiling = value).withInheritedFields(this)
  }

  def withBabelTranspiling(value: Boolean): Config = {
    copy(babelTranspiling = value).withInheritedFields(this)
  }

  def withVueTranspiling(value: Boolean): Config = {
    copy(vueTranspiling = value).withInheritedFields(this)
  }

  def withNuxtTranspiling(value: Boolean): Config = {
    copy(nuxtTranspiling = value).withInheritedFields(this)
  }

  def withTemplateTranspiling(value: Boolean): Config = {
    copy(templateTranspiling = value).withInheritedFields(this)
  }

  def withPackageJsonLocation(value: String): Config = {
    copy(packageJsonLocation = value).withInheritedFields(this)
  }

  def withTsTypes(value: Boolean): Config = {
    copy(withTsTypes = value).withInheritedFields(this)
  }

  def withIgnoreMinified(value: Boolean): Config = {
    copy(ignoreMinified = value).withInheritedFields(this)
  }

  def withIgnoreTests(value: Boolean): Config = {
    copy(ignoreTests = value).withInheritedFields(this)
  }

  def withIgnorePrivateDeps(value: Boolean): Config = {
    copy(ignorePrivateDeps = value).withInheritedFields(this)
  }

  def withPrivateDeps(value: Seq[String]): Config = {
    copy(privateDeps = value).withInheritedFields(this)
  }

  def withIncludeHtml(value: Boolean): Config = {
    copy(includeHtml = value).withInheritedFields(this)
  }

  def withJvmMetrics(value: Option[Int]): Config = {
    copy(jvmMetrics = value).withInheritedFields(this)
  }

  def withModuleMode(value: Option[String]): Config = {
    copy(moduleMode = value).withInheritedFields(this)
  }

  def withNodeModuleFolder(value: Boolean): Config = {
    copy(withNodeModuleFolder = value).withInheritedFields(this)
  }

  def withOptimizeDependencies(value: Boolean): Config = {
    copy(optimizeDependencies = value).withInheritedFields(this)
  }

  def withFixedTranspilationDependencies(value: Boolean): Config = {
    copy(fixedTranspilationDependencies = value).withInheritedFields(this)
  }

  override def withInputPath(inputPath: String): Config = {
    super.withInputPath(inputPath).withLoadedIgnores().withInheritedFields(this)
  }

  private def withLoadedIgnores(): Config = {
    val slIngoreFilePath = Paths.get(inputPath, Config.SL_IGNORE_FILE)
    Try(IOUtils.readLinesInFile(slIngoreFilePath)) match {
      case Failure(_)     => this
      case Success(lines) => this.withIgnoredFiles(this.ignoredFiles ++ lines).withInheritedFields(this)
    }
  }

  override def toString: String =
    s"""
      |\t- Source project: '$inputPath'
      |\t- package.json location: '${createPathForPackageJson()}'
      |\t- Module mode: '${moduleMode.getOrElse(TypescriptTranspiler.DefaultModule)}'
      |\t- Optimize dependencies: $optimizeDependencies
      |\t- Fixed transpilations dependencies: $fixedTranspilationDependencies
      |\t- Typescript transpiling: $tsTranspiling
      |\t- Babel transpiling: $babelTranspiling
      |\t- Vue.js transpiling: $vueTranspiling
      |\t- Nuxt.js transpiling: $nuxtTranspiling
      |\t- Template transpiling: $templateTranspiling
      |\t- Ignored files: ${ignoredFiles
        .filter(f => new File(f).isFile)
        .map(f => s"${System.lineSeparator()}\t\t'$f'")
        .mkString}
      |\t- Ignored files regex: '$ignoredFilesRegex'
      |\t- Ignored folders: ${ignoredFiles
        .filter(f => new File(f).isDirectory)
        .map(f => s"${System.lineSeparator()}\t\t'$f'")
        .mkString}
      |\t- Ignore minified files: $ignoreMinified
      |\t- Ignore test files: $ignoreTests
      |\t- Ignore private dependencies: $ignorePrivateDeps
      |\t- Additional private dependencies: ${privateDeps
        .map(f => s"${System.lineSeparator()}\t\t'$f'")
        .mkString}
      |\t- Include HTML files: $includeHtml
      |\t- Output file: '$outputPath'
      |""".stripMargin
}

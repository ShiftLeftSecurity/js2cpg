package io.shiftleft.js2cpg.core

import java.nio.file.{Path, StandardCopyOption}
import better.files.File
import better.files.File.LinkOptions
import io.shiftleft.js2cpg.cpg.passes._
import io.shiftleft.js2cpg.io.FileDefaults._
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.js2cpg.parser.PackageJsonParser
import io.shiftleft.js2cpg.preprocessing.TranspilationRunner
import io.shiftleft.js2cpg.util.MemoryMetrics
import io.shiftleft.passes.{IntervalKeyPool, KeyPoolCreator}
import io.shiftleft.x2cpg.X2Cpg.newEmptyCpg
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

class Js2Cpg {

  private val logger = LoggerFactory.getLogger(getClass)

  private val report = new Report()

  private def checkCpgGenInputFiles(jsFiles: List[(Path, Path)], config: Config): Unit = {
    if (jsFiles.isEmpty) {
      val project = File(config.srcDir)
      logger.warn(s"'$project' contains no *.js files. No CPG was generated.")
      if (config.babelTranspiling) {
        logger.warn("\t- Babel transpilation did not yield any *.js files")
      }
      if (config.tsTranspiling) {
        logger.warn(
          "\t- Typescript compilation did not yield any *.js files. Does a valid 'tsconfig.json' exist in that folder?")
      }
      if (config.vueTranspiling) {
        logger.warn("\t- Vue.js transpilation did not yield any *.js files.")
      }
      if (config.templateTranspiling) {
        logger.warn("\t- Template transpilation did not yield any *.js files.")
      }
      System.exit(1)
    }
  }

  private def handleVsixProject(project: File, tmpProjectDir: File): File = {
    logger.debug(
      s"Project is a VS code extension file (*$VSIX_SUFFIX). Unpacking it to '$tmpProjectDir'.")
    project.streamedUnzip(tmpProjectDir) / "extension"
  }

  private def handleStandardProject(project: File, tmpProjectDir: File, config: Config): File = {
    val realProjectPath = File(project.path.toRealPath())
    if (realProjectPath == tmpProjectDir) {
      realProjectPath
    } else {
      logger.debug(s"Copying '$realProjectPath' to temporary workspace '$tmpProjectDir'.")
      Try(FileUtils.copyToDirectory(realProjectPath, tmpProjectDir, config)) match {
        case Failure(_) =>
          logger.debug(
            s"Unable to copy project to temporary workspace '$tmpProjectDir'. Does it contain broken symlinks?")
          logger.debug(
            s"Retrying to copy '$realProjectPath' to temporary workspace '$tmpProjectDir' without symlinks.")
          FileUtils.copyToDirectory(realProjectPath, tmpProjectDir, config)(
            copyOptions = Seq(StandardCopyOption.REPLACE_EXISTING) ++ LinkOptions.noFollow)
        case Success(value) => value
      }
    }
  }

  private def findProjects(projectDir: File, config: Config): List[Path] = {
    val allProjects = FileUtils
      .getFileTree(projectDir.path, config, ".json")
      .filter(_.toString.endsWith(PackageJsonParser.PACKAGE_JSON_FILENAME))
      .map(_.getParent)

    val subProjects = Set.from(allProjects) - projectDir.path

    allProjects match {
      case Nil =>
        List(projectDir.path)
      case head :: Nil if head == projectDir.path =>
        List(head)
      case _ =>
        logger.info(s"Found the following sub-projects:${subProjects
          .map(
            p => projectDir.relativize(p)
          )
          .mkString("\n\t- ", "\n\t- ", "")}")
        projectDir.path +: subProjects.toList
    }
  }

  private def isInCi: Boolean = sys.env.get("CI").contains("true")

  private def prepareAndGenerateCpg(project: File, tmpProjectDir: File, config: Config): Unit = {
    val newTmpProjectDir = if (project.extension.contains(VSIX_SUFFIX)) {
      handleVsixProject(project, tmpProjectDir)
    } else {
      handleStandardProject(project, tmpProjectDir, config)
    }

    FileUtils.logAndClearExcludedPaths()

    File.usingTemporaryDirectory("js2cpgTranspileOut") { tmpTranspileDir =>
      val jsFiles = findProjects(newTmpProjectDir, config)
        .flatMap { p =>
          val subDir =
            if (p.toString != newTmpProjectDir.toString()) Some(project.relativize(p)) else None
          new TranspilationRunner(p, tmpTranspileDir.path, config, subDir = subDir).execute()
        }
        .distinctBy(_._1)

      FileUtils.logAndClearExcludedPaths()
      checkCpgGenInputFiles(jsFiles, config)

      // Memory metrics are only recorded for the actual CPG generation.
      // It does not make much sense to also do that when transpiling (see above)
      // as we do not have any control of the transpilers themselves
      // (also they very much depend on the speed of npm).
      MemoryMetrics.withMemoryMetrics(config) {
        generateCPG(config.copy(srcDir = newTmpProjectDir.toString), jsFiles)
      }
    }
  }

  def run(config: Config): Unit = {
    val project = File(config.srcDir)

    // We need to get the absolut project path here otherwise user configured
    // excludes based on either absolut or relative paths can not be matched.
    // We intentionally use .canonicalFile as this is using java.io.File#getCanonicalPath
    // under the hood that will resolve . or ../ and symbolic links in any combination.
    val absoluteProjectPath          = project.canonicalFile.pathAsString
    val configWithAbsolutProjectPath = config.copy(srcDir = absoluteProjectPath)

    logger.info(s"Generating CPG from Javascript sources in: '$absoluteProjectPath'")
    logger.debug(s"Configuration:$configWithAbsolutProjectPath")

    if (isInCi) {
      prepareAndGenerateCpg(project, File(absoluteProjectPath), configWithAbsolutProjectPath)
    } else {
      File.usingTemporaryDirectory(project.name) { tmpProjectDir =>
        prepareAndGenerateCpg(project, tmpProjectDir, configWithAbsolutProjectPath)
      }
    }

    logger.info("Generation of CPG is complete.")
    report.print()
  }

  private def configFiles(config: Config, extensions: List[String]): List[(Path, Path)] =
    extensions.flatMap(
      FileUtils
        .getFileTree(File(config.srcDir).path, config, _, filterIgnoredFiles = false)
        .map(f => (f, File(config.srcDir).path))
    )

  private def generateCPG(config: Config, jsFilesWithRoot: List[(Path, Path)]): Unit = {
    val metaDataKeyPool     = new IntervalKeyPool(1, 100)
    val builtinTypesKeyPool = new IntervalKeyPool(101, 200)
    val dependenciesKeyPool = new IntervalKeyPool(201, 1000100)

    val otherPools             = KeyPoolCreator.obtain(5, 1000101)
    val functionKeyPool        = otherPools.head
    val vueAsConfigPassPool    = otherPools(1)
    val configPassPool         = otherPools(2)
    val privateKeyFilePassPool = otherPools(3)
    val htmlAsConfigPassPool   = otherPools(4)

    val cpg = newEmptyCpg(Some(config.outputFile))

    new AstCreationPass(File(config.srcDir), jsFilesWithRoot, cpg, functionKeyPool, report)
      .createAndApply()

    new JsMetaDataPass(cpg, metaDataKeyPool).createAndApply()
    new BuiltinTypesPass(cpg, builtinTypesKeyPool).createAndApply()
    new DependenciesPass(cpg, config, dependenciesKeyPool)
      .createAndApply()
    new ConfigPass(configFiles(config, List(VUE_SUFFIX)), cpg, vueAsConfigPassPool, report)
      .createAndApply()
    new PrivateKeyFilePass(configFiles(config, List(KEY_SUFFIX)),
                           cpg,
                           privateKeyFilePassPool,
                           report)
      .createAndApply()

    if (config.includeHtml) {
      new ConfigPass(configFiles(config, List(HTML_SUFFIX)), cpg, htmlAsConfigPassPool, report)
        .createAndApply()
    }

    if (config.includeConfigs) {
      new ConfigPass(configFiles(config, CONFIG_FILES), cpg, configPassPool, report)
        .createAndApply()
    }

    cpg.close()
  }

}

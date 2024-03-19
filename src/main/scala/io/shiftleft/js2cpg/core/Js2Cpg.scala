package io.shiftleft.js2cpg.core

import java.nio.file.{Path, StandardCopyOption}
import better.files.File
import better.files.File.LinkOptions
import io.shiftleft.js2cpg.passes.*
import io.shiftleft.js2cpg.io.FileDefaults.*
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.js2cpg.parser.PackageJsonParser
import io.shiftleft.js2cpg.preprocessing.NuxtTranspiler
import io.shiftleft.js2cpg.preprocessing.TranspilationRunner
import io.shiftleft.js2cpg.utils.MemoryMetrics
import io.joern.x2cpg.X2Cpg.withNewEmptyCpg
import io.joern.x2cpg.utils.HashUtil
import io.joern.x2cpg.utils.Report
import io.joern.x2cpg.X2CpgFrontend
import io.shiftleft.codepropertygraph.Cpg
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

class Js2Cpg extends X2CpgFrontend[Config] {

  private val logger = LoggerFactory.getLogger(getClass)

  private val report = new Report()

  private def checkCpgGenInputFiles(jsFiles: List[(Path, Path)], config: Config): Unit = {
    if (jsFiles.isEmpty) {
      val project = File(config.inputPath)
      logger.warn(s"'$project' contains no *.js files. No CPG was generated.")
      if (config.babelTranspiling) {
        logger.warn("\t- Babel transpilation did not yield any *.js files")
      }
      if (config.tsTranspiling) {
        logger.warn(
          "\t- Typescript compilation did not yield any *.js files. Does a valid 'tsconfig.json' exist in that folder?"
        )
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
    logger.debug(s"Project is a VS code extension file (*$VSIX_SUFFIX). Unpacking it to '$tmpProjectDir'.")
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
            s"Unable to copy project to temporary workspace '$tmpProjectDir'. Does it contain broken symlinks?"
          )
          logger.debug(s"Retrying to copy '$realProjectPath' to temporary workspace '$tmpProjectDir' without symlinks.")
          FileUtils.copyToDirectory(realProjectPath, tmpProjectDir, config)(copyOptions =
            Seq(StandardCopyOption.REPLACE_EXISTING) ++ LinkOptions.noFollow
          )
        case Success(value) => value
      }
    }
  }

  private def findProjects(projectDir: File, config: Config): List[Path] = {
    val allProjects = FileUtils
      .getFileTree(projectDir.path, config, List(".json"))
      .filter(PackageJsonParser.isValidProjectPackageJson)
      .map(_.getParent)
      .sortBy(_.toString)

    val subProjects = Set.from(allProjects) - projectDir.path

    allProjects match {
      case Nil =>
        List(projectDir.path)
      case head :: Nil if head == projectDir.path =>
        List(head)
      case _ =>
        logger.info(s"Found the following sub-projects:${subProjects
            .map(p => projectDir.relativize(p))
            .mkString("\n\t- ", "\n\t- ", "")}")
        projectDir.path +: subProjects.toList
    }
  }

  private def collectJsFiles(jsFiles: List[(Path, Path)], dir: Path, config: Config): List[(Path, Path)] = {
    val transpiledJsFiles = FileUtils
      .getFileTree(dir, config, List(JS_SUFFIX, MJS_SUFFIX))
      .map(f => (f, dir))
    jsFiles.filterNot { case (f, rootDir) =>
      val filename = f.toString.replace(rootDir.toString, "")
      transpiledJsFiles.exists(_._1.toString.endsWith(filename))
    } ++ transpiledJsFiles
  }

  private def prepareAndGenerateCpg(project: File, tmpProjectDir: File, config: Config): Try[Cpg] = {
    val newTmpProjectDir = if (project.extension.contains(VSIX_SUFFIX)) {
      handleVsixProject(project, tmpProjectDir)
    } else {
      handleStandardProject(project, tmpProjectDir, config)
    }

    FileUtils.logAndClearExcludedPaths()

    val jsFilesBeforeTranspiling = FileUtils
      .getFileTree(newTmpProjectDir.path, config, List(JS_SUFFIX, MJS_SUFFIX))
      .map(f => (f, newTmpProjectDir.path))

    val result = for {
      tmpTranspileDir <- File.temporaryDirectory("js2cpgTranspileOut")
    } yield {
      findProjects(newTmpProjectDir, config)
        .foreach { p =>
          val subDir =
            if (p.toString != newTmpProjectDir.toString()) Some(newTmpProjectDir.relativize(p))
            else None
          new TranspilationRunner(p, tmpTranspileDir.path, config, subDir = subDir).execute()
        }

      val jsFilesAfterTranspiling =
        collectJsFiles(jsFilesBeforeTranspiling, tmpTranspileDir.path, config) ++
          NuxtTranspiler.collectJsFiles(newTmpProjectDir.path, config)

      val privateModuleFiles = if (!config.ignorePrivateDeps) {
        new TranspilationRunner(newTmpProjectDir.path, tmpTranspileDir.path, config)
          .handlePrivateModules()
      } else {
        Nil
      }

      val jsFiles = (jsFilesAfterTranspiling ++ privateModuleFiles).distinctBy(_._1)

      FileUtils.logAndClearExcludedPaths()
      checkCpgGenInputFiles(jsFiles, config)

      // Memory metrics are only recorded for the actual CPG generation.
      // It does not make much sense to also do that when transpiling (see above)
      // as we do not have any control of the transpilers themselves
      // (also they very much depend on the speed of npm).
      MemoryMetrics.withMemoryMetrics(config) {
        generateCPG(config.withInputPath(newTmpProjectDir.toString), jsFiles)
      }
    }
    result.get()
  }

  def createCpg(config: Config): Try[Cpg] = {
    val project = File(config.inputPath)

    // We need to get the absolut project path here otherwise user configured
    // excludes based on either absolut or relative paths can not be matched.
    // We intentionally use .canonicalFile as this is using java.io.File#getCanonicalPath
    // under the hood that will resolve . or ../ and symbolic links in any combination.
    val absoluteProjectPath          = project.canonicalFile.pathAsString
    val configWithAbsolutProjectPath = config.withInputPath(absoluteProjectPath)

    logger.info(s"Generating CPG from Javascript sources in: '$absoluteProjectPath'")
    logger.debug(s"Configuration:$configWithAbsolutProjectPath")

    val result = for {
      tmpProjectDir <- File.temporaryDirectory(project.name)
    } yield prepareAndGenerateCpg(project, tmpProjectDir, configWithAbsolutProjectPath)

    logger.info("Generation of CPG is complete.")
    report.print()

    result.get()
  }

  private def configFiles(config: Config, extensions: List[String]): List[(Path, Path)] =
    FileUtils
      .getFileTree(File(config.inputPath).path, config, extensions, filterIgnoredFiles = false)
      .map(f => (f, File(config.inputPath).path))

  private def generateCPG(config: Config, jsFilesWithRoot: List[(Path, Path)]): Try[Cpg] = {
    withNewEmptyCpg(config.outputPath, config) { (cpg, config) =>
      val hash = HashUtil.sha256(jsFilesWithRoot.map(_._1))

      new AstCreationPass(cpg, jsFilesWithRoot, config, report).createAndApply()
      new JsMetaDataPass(cpg, hash, config.inputPath).createAndApply()
      new BuiltinTypesPass(cpg).createAndApply()
      new DependenciesPass(cpg, config).createAndApply()
      new ConfigPass(configFiles(config, List(VUE_SUFFIX)), cpg, report).createAndApply()
      new PrivateKeyFilePass(configFiles(config, List(KEY_SUFFIX)), cpg, report).createAndApply()
      if (config.includeHtml) {
        new ConfigPass(configFiles(config, List(HTML_SUFFIX)), cpg, report).createAndApply()
      }
      new ConfigPass(configFiles(config, CONFIG_FILES), cpg, report).createAndApply()
    }
  }

}

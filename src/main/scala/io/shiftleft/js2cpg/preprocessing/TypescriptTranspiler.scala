package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.TS_SUFFIX
import io.shiftleft.js2cpg.io.{ExternalCommand, FileUtils}
import io.shiftleft.js2cpg.parser.TsConfigJsonParser
import org.slf4j.LoggerFactory
import org.apache.commons.io.{FileUtils => CommonsFileUtils}

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success, Try}

object TypescriptTranspiler {

  val COMMONJS: String = "commonjs"
  val ESNEXT: String   = "esnext"
  val ES2020: String   = "es2020"

  val DEFAULT_MODULE: String = COMMONJS

}

class TypescriptTranspiler(override val config: Config,
                           override val projectPath: Path,
                           subDir: Option[Path] = None)
    extends Transpiler
    with NpmEnvironment {

  private val logger = LoggerFactory.getLogger(getClass)

  private val NODE_OPTIONS: Map[String, String] = Map("NODE_OPTIONS" -> "--max_old_space_size=4096")

  private val tsc = Paths.get(projectPath.toString, "node_modules", ".bin", "tsc")

  private def hasTsFiles: Boolean =
    FileUtils.getFileTree(projectPath, config, TS_SUFFIX).nonEmpty

  override def shouldRun(): Boolean =
    config.tsTranspiling && (File(projectPath) / "tsconfig.json").exists && hasTsFiles && !isVueProject

  private def moveIgnoredDirs(from: File, to: File): Unit = {
    val ignores = if (config.ignoreTests) {
      DEFAULT_IGNORED_DIRS ++ DEFAULT_IGNORED_TEST_DIRS
    } else {
      DEFAULT_IGNORED_DIRS
    }
    ignores.foreach { dir =>
      val ignoredDir = from / dir
      if (ignoredDir.isDirectory) {
        Try(CommonsFileUtils.moveDirectory(ignoredDir.toJava, (to / dir).toJava)) match {
          case Failure(exception) =>
            logger.debug(s"Could not move '$ignoredDir' to '$to' during Typescript transpilation!" +
                           " Please check the permissions for that directory.",
                         exception)
          case Success(_) => // this is fine
        }
      }
    }
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    File.usingTemporaryDirectory() { tmpForIgnoredDirs =>
      // Sadly, tsc does not allow to exclude folders when being run from cli.
      // Hence, we have to move ignored folders to a temporary folder ...
      moveIgnoredDirs(File(projectPath), tmpForIgnoredDirs)

      val isSolutionTsConfig = TsConfigJsonParser.isSolutionTsConfig(projectPath, tsc.toString)
      val projects = if (isSolutionTsConfig) {
        TsConfigJsonParser.subprojects(projectPath, tsc.toString)
      } else {
        "" :: Nil
      }

      val module = config.moduleMode.getOrElse(TsConfigJsonParser.module(projectPath, tsc.toString))
      val outDir =
        subDir.map(s => File(tmpTranspileDir.toString, s.toString)).getOrElse(File(tmpTranspileDir))

      for (proj <- projects) {
        val projCommand = if (proj.nonEmpty) s"--project $proj" else ""
        val projOutDir =
          if (proj.nonEmpty) outDir / proj.substring(0, proj.lastIndexOf("/")) else outDir
        val command =
          s"$tsc -sourcemap --outDir $projOutDir -t ES2015 -m $module --jsx react --noEmit false $projCommand"
        logger.debug(
          s"\t+ TypeScript compiling $projectPath $projCommand to $projOutDir (using $module style modules)")
        ExternalCommand.run(command, projectPath.toString, extraEnv = NODE_OPTIONS) match {
          case Success(result) =>
            logger.debug(s"\t+ TypeScript compiling finished. $result")
          case Failure(exception) =>
            logger.debug(s"\t- TypeScript compiling failed: ${exception.getMessage}")
        }
      }

      // ... and copy them back afterward.
      moveIgnoredDirs(tmpForIgnoredDirs, File(projectPath))
    }
    true
  }

  override def validEnvironment(): Boolean = valid()

  override protected def logExecution(): Unit =
    logger.info(s"TypeScript - transpiling source files in '${File(projectPath).name}'")

}

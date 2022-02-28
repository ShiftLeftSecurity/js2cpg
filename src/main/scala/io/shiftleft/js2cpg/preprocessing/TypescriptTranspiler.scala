package io.shiftleft.js2cpg.preprocessing

import better.files.File
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
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

class TypescriptTranspiler(override val config: Config, override val projectPath: Path, subDir: Option[Path] = None)
    extends Transpiler {

  private val logger = LoggerFactory.getLogger(getClass)

  private val NODE_OPTIONS: Map[String, String] = Map("NODE_OPTIONS" -> "--max_old_space_size=4096")

  private val tsc = Paths.get(projectPath.toString, "node_modules", ".bin", "tsc").toString

  private def hasTsFiles: Boolean =
    FileUtils.getFileTree(projectPath, config, List(TS_SUFFIX)).nonEmpty

  override def shouldRun(): Boolean =
    config.tsTranspiling &&
      (File(projectPath) / "tsconfig.json").exists &&
      hasTsFiles

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
            logger.debug(
              s"Could not move '$ignoredDir' to '$to' during Typescript transpilation!" +
                " Please check the permissions for that directory.",
              exception
            )
          case Success(_) => // this is fine
        }
      }
    }
  }

  private def removeComments(json: String): String = {
    val mapper = new ObjectMapper
    mapper.enable(JsonParser.Feature.ALLOW_COMMENTS)
    mapper.writeValueAsString(mapper.readTree(json))
  }

  private def createCustomTsConfigFile(): Try[File] = {
    val customTsConfigFilePath = (File(projectPath) / "tsconfig.json").path
    Try {
      val content = FileUtils.readLinesInFile(customTsConfigFilePath).mkString("\n")
      val mapper  = new ObjectMapper()
      val json    = mapper.readTree(removeComments(content))
      // --include is not available as tsc CLI argument; we set it manually:
      Option(json.get("compilerOptions")).foreach(_.asInstanceOf[ObjectNode].remove("sourceRoot"))
      json.asInstanceOf[ObjectNode].putArray("include").add("**/*")
      val customTsConfigFile =
        File
          .newTemporaryFile("js2cpgTsConfig", ".json", parent = Some(projectPath))
          .deleteOnExit(swallowIOExceptions = true)
      customTsConfigFile.writeText(mapper.writeValueAsString(json))
    }
  }

  private def installTsPlugins(): Boolean = {
    val command = if (yarnAvailable()) {
      s"${TranspilingEnvironment.YARN_ADD} typescript"
    } else {
      s"${TranspilingEnvironment.NPM_INSTALL} typescript"
    }
    logger.info("Installing TypeScript dependencies and plugins. That will take a while.")
    logger.debug(s"\t+ Installing Typescript plugins with command '$command' in path '$projectPath'")
    ExternalCommand.run(command, projectPath.toString, extraEnv = NODE_OPTIONS) match {
      case Success(_) =>
        logger.info("\t+ TypeScript plugins installed")
        true
      case Failure(exception) =>
        logger.error("\t- Failed to install TypeScript plugins", exception)
        false
    }
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    if (installTsPlugins()) {
      File.usingTemporaryDirectory() { tmpForIgnoredDirs =>
        // Sadly, tsc does not allow to exclude folders when being run from cli.
        // Hence, we have to move ignored folders to a temporary folder ...
        moveIgnoredDirs(File(projectPath), tmpForIgnoredDirs)

        val isSolutionTsConfig = TsConfigJsonParser.isSolutionTsConfig(projectPath, tsc)
        val projects = if (isSolutionTsConfig) {
          TsConfigJsonParser.subprojects(projectPath, tsc)
        } else {
          "" :: Nil
        }

        val module = config.moduleMode.getOrElse(TsConfigJsonParser.module(projectPath, tsc))
        val outDir =
          subDir
            .map(s => File(tmpTranspileDir.toString, s.toString))
            .getOrElse(File(tmpTranspileDir))

        for (proj <- projects) {
          val projCommand = if (proj.nonEmpty) {
            s"--project $proj"
          } else {
            // for the root project we try to create a custom tsconfig file to ignore settings that may be there
            // and that we sadly cannot override with tsc directly:
            createCustomTsConfigFile() match {
              case Failure(f) =>
                logger.debug("\t- Creating a custom TS config failed", f)
                ""
              case Success(customTsConfigFile) => s"--project $customTsConfigFile"
            }
          }

          val projOutDir =
            if (proj.nonEmpty) outDir / proj.substring(0, proj.lastIndexOf("/")) else outDir
          val sourceRoot =
            if (proj.nonEmpty)
              s"--sourceRoot ${File(projectPath) / proj.substring(0, proj.lastIndexOf("/"))}"
            else ""

          val command =
            s"${ExternalCommand.toOSCommand(tsc)} -sourcemap $sourceRoot --outDir $projOutDir -t ES2017 -m $module --jsx react --noEmit false $projCommand"
          logger.debug(
            s"\t+ TypeScript compiling $projectPath $projCommand to $projOutDir (using $module style modules)"
          )

          ExternalCommand.run(command, projectPath.toString, extraEnv = NODE_OPTIONS) match {
            case Success(_)         => logger.debug("\t+ TypeScript compiling finished")
            case Failure(exception) => logger.debug("\t- TypeScript compiling failed", exception)
          }
        }

        // ... and copy them back afterward.
        moveIgnoredDirs(tmpForIgnoredDirs, File(projectPath))
      }
    }
    true
  }

  override def validEnvironment(): Boolean = valid()

  override protected def logExecution(): Unit =
    logger.info(s"TypeScript - transpiling source files in '${File(projectPath).name}'")

}

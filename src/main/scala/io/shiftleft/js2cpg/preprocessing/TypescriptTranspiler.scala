package io.shiftleft.js2cpg.preprocessing

import better.files.File
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.TS_SUFFIX
import io.shiftleft.js2cpg.io.{ExternalCommand, FileUtils}
import io.shiftleft.js2cpg.parser.PackageJsonParser
import io.shiftleft.js2cpg.parser.TsConfigJsonParser
import io.shiftleft.js2cpg.preprocessing.TypescriptTranspiler.DEFAULT_MODULE
import io.shiftleft.js2cpg.preprocessing.TypescriptTranspiler.DENO_CONFIG
import io.shiftleft.utils.IOUtils
import org.slf4j.LoggerFactory
import org.apache.commons.io.{FileUtils => CommonsFileUtils}

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success, Try}

object TypescriptTranspiler {

  val DEFAULT_MODULE: String = "commonjs"

  private val tscTypingWarnings =
    List("error TS", ".d.ts", "The file is in the program because", "Entry point of type library")

  val DENO_CONFIG: String = "deno.json"

}

class TypescriptTranspiler(override val config: Config, override val projectPath: Path, subDir: Option[Path] = None)
    extends Transpiler {

  private val logger = LoggerFactory.getLogger(getClass)

  private val NODE_OPTIONS: Map[String, String] = Map("NODE_OPTIONS" -> "--max_old_space_size=4096")

  private val tsc                  = Paths.get(projectPath.toString, "node_modules", ".bin", "tsc").toString
  private val typescriptAndVersion = Versions.nameAndVersion("typescript")

  private def hasTsFiles: Boolean =
    FileUtils.getFileTree(projectPath, config, List(TS_SUFFIX)).nonEmpty

  private def isFreshProject: Boolean = (File(projectPath) / DENO_CONFIG).exists

  private def isTsProject: Boolean =
    (File(projectPath) / "tsconfig.json").exists || isFreshProject

  override def shouldRun(): Boolean =
    config.tsTranspiling && isTsProject && hasTsFiles

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

  private def createCustomTsConfigFile(): Try[File] = {
    val tsConfigFilePath = (File(projectPath) / "tsconfig.json").path
    Try {
      val content = IOUtils.readLinesInFile(tsConfigFilePath).mkString("\n")
      val mapper  = new ObjectMapper()
      val json    = mapper.readTree(PackageJsonParser.removeComments(content))
      Option(json.get("compilerOptions")).foreach { options =>
        options.asInstanceOf[ObjectNode].remove("sourceRoot")
        options.asInstanceOf[ObjectNode].putArray("types")
        options.asInstanceOf[ObjectNode].putArray("typeRoots")
      }
      // --include is not available as tsc CLI argument; we set it manually:
      json.asInstanceOf[ObjectNode].putArray("include").add("**/*")
      val customTsConfigFile = File.newTemporaryFile("js2cpgTsConfig", ".json", parent = Some(projectPath))
      customTsConfigFile.writeText(mapper.writeValueAsString(json))
    }
  }

  private def installTsPlugins(): Boolean = {
    val command = if (pnpmAvailable(projectPath)) {
      s"${TranspilingEnvironment.PNPM_ADD} $typescriptAndVersion"
    } else if (yarnAvailable()) {
      s"${TranspilingEnvironment.YARN_ADD} $typescriptAndVersion"
    } else {
      s"${TranspilingEnvironment.NPM_INSTALL} $typescriptAndVersion"
    }
    logger.info("Installing TypeScript dependencies and plugins. That will take a while.")
    logger.debug(s"\t+ Installing Typescript plugins with command '$command' in path '$projectPath'")
    ExternalCommand.run(command, projectPath.toString, extraEnv = NODE_OPTIONS) match {
      case Success(_) =>
        logger.info("\t+ TypeScript plugins installed")
        true
      case Failure(exception) =>
        logger.warn("\t- Failed to install TypeScript plugins", exception)
        false
    }
  }

  private def isCleanTrace(exception: Throwable): Boolean =
    exception.getMessage.linesIterator.forall(l => TypescriptTranspiler.tscTypingWarnings.exists(l.contains))

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    if (installTsPlugins()) {
      File.usingTemporaryDirectory() { tmpForIgnoredDirs =>
        if (isFreshProject) {
          // Fresh projects do not need a separate tsconfig, but tsc needs at least an empty one
          (File(projectPath) / "tsconfig.json")
            .touch()
            .write("{}")
            .deleteOnExit(swallowIOExceptions = true)
        }
        // Sadly, tsc does not allow to exclude folders when being run from cli.
        // Hence, we have to move ignored folders to a temporary folder ...
        moveIgnoredDirs(File(projectPath), tmpForIgnoredDirs)

        val isSolutionTsConfig = TsConfigJsonParser.isSolutionTsConfig(projectPath, tsc)
        val projects = if (isSolutionTsConfig) {
          TsConfigJsonParser.subprojects(projectPath, tsc)
        } else {
          "" :: Nil
        }

        val module = config.moduleMode.getOrElse(DEFAULT_MODULE)
        val outDir = subDir.map(s => File(tmpTranspileDir.toString, s.toString)).getOrElse(File(tmpTranspileDir))

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
            s"${ExternalCommand.toOSCommand(tsc)} --skipLibCheck -sourcemap $sourceRoot --outDir $projOutDir -t ES2017 -m $module --jsx react --noEmit false $projCommand"
          logger.debug(
            s"\t+ TypeScript compiling $projectPath $projCommand to $projOutDir (using $module style modules)"
          )

          ExternalCommand.run(command, projectPath.toString, extraEnv = NODE_OPTIONS) match {
            case Success(_) =>
              logger.debug("\t+ TypeScript compiling finished")
            case Failure(exception) if isCleanTrace(exception) =>
              logger.debug("\t+ TypeScript compiling finished")
            case Failure(exception) =>
              logger.debug(s"\t- TypeScript compiling failed: $exception")
          }
        }

        // ... and copy them back afterward.
        moveIgnoredDirs(tmpForIgnoredDirs, File(projectPath))
        // ... and remove the temporary tsconfig.json
        File(projectPath)
          .walk(maxDepth = 1)
          .find(_.toString().contains("js2cpgTsConfig"))
          .foreach(_.delete(swallowIOExceptions = true))
      }
    }
    true
  }

  override def validEnvironment(): Boolean = valid(projectPath)

  override protected def logExecution(): Unit =
    logger.info(s"TypeScript - transpiling source files in '${File(projectPath).name}'")

}

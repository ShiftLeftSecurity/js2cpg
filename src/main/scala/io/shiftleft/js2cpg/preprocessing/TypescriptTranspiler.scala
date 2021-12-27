package io.shiftleft.js2cpg.preprocessing

import better.files.File
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.TS_SUFFIX
import io.shiftleft.js2cpg.io.{ExternalCommand, FileUtils}
import io.shiftleft.js2cpg.parser.TsConfigJsonParser
import org.slf4j.LoggerFactory
import org.apache.commons.io.{FileUtils => CommonsFileUtils}
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.JsString

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success, Try}
import scala.util.Using

object TypescriptTranspiler {

  val COMMONJS: String = "commonjs"
  val ESNEXT: String   = "esnext"
  val ES2020: String   = "es2020"

  val DEFAULT_MODULE: String = COMMONJS

}

class TypescriptTranspiler(override val config: Config,
                           override val projectPath: Path,
                           subDir: Option[Path] = None)
    extends Transpiler {

  private val logger = LoggerFactory.getLogger(getClass)

  private val NODE_OPTIONS: Map[String, String] = Map("NODE_OPTIONS" -> "--max_old_space_size=4096")

  private val tsc = Paths.get(projectPath.toString, "node_modules", ".bin", "tsc").toString

  private def hasTsFiles: Boolean =
    FileUtils.getFileTree(projectPath, config, List(TS_SUFFIX)).nonEmpty

  override def shouldRun(): Boolean =
    config.tsTranspiling &&
      (File(projectPath) / "tsconfig.json").exists &&
      hasTsFiles &&
      !VueTranspiler.isVueProject(config, projectPath)

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

  private def removeComments(json: String): String = {
    val mapper = new ObjectMapper
    mapper.enable(JsonParser.Feature.ALLOW_COMMENTS)
    mapper.writeValueAsString(mapper.readTree(json))
  }

  private def createCustomTsConfigFile() =
    Using(FileUtils.bufferedSourceFromFile((File(projectPath) / "tsconfig.json").path)) {
      bufferedSource =>
        val content = FileUtils.contentFromBufferedSource(bufferedSource)
        val json    = Json.parse(removeComments(content))
        val compilerOptions =
          json
            .as[JsObject]
            .value
            .get("compilerOptions")
            .map(_.as[JsObject] - "sourceRoot")
            .getOrElse(JsObject.empty)
        // --include is not available as tsc CLI argument; we set it manually:
        val jsonCleaned = json
          .as[JsObject] + ("include" -> JsArray(Array(JsString("**/*")))) + ("compilerOptions" -> compilerOptions)
        val customTsConfigFile =
          File.newTemporaryFile("js2cpgTsConfig", ".json", parent = Some(projectPath))
        customTsConfigFile.writeText(Json.stringify(jsonCleaned))
    }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
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
        subDir.map(s => File(tmpTranspileDir.toString, s.toString)).getOrElse(File(tmpTranspileDir))

      for (proj <- projects) {
        val (customTsConfigFile, projCommand) = if (proj.nonEmpty) {
          (None, s"--project $proj")
        } else {
          // for the root project we try to create a custom tsconfig file to ignore settings that may be there
          // and that we sadly cannot override with tsc directly:
          createCustomTsConfigFile() match {
            case Failure(f) =>
              logger.debug("\t- Creating a custom TS config failed", f)
              (None, "")
            case Success(customTsConfigFile) =>
              (Some(customTsConfigFile), s"--project $customTsConfigFile")
          }
        }

        val projOutDir =
          if (proj.nonEmpty) outDir / proj.substring(0, proj.lastIndexOf("/")) else outDir
        val sourceRoot =
          if (proj.nonEmpty)
            s"--sourceRoot ${File(projectPath) / proj.substring(0, proj.lastIndexOf("/"))}"
          else ""

        val command =
          s"${ExternalCommand.toOSCommand(tsc)} -sourcemap $sourceRoot --outDir $projOutDir -t ES2015 -m $module --jsx react --noEmit false $projCommand"
        logger.debug(
          s"\t+ TypeScript compiling $projectPath $projCommand to $projOutDir (using $module style modules)")

        ExternalCommand.run(command, projectPath.toString, extraEnv = NODE_OPTIONS) match {
          case Success(_)         => logger.debug("\t+ TypeScript compiling finished")
          case Failure(exception) => logger.debug("\t- TypeScript compiling failed", exception)
        }
        customTsConfigFile.foreach(_.delete(swallowIOExceptions = true))
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

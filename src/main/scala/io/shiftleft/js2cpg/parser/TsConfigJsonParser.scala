package io.shiftleft.js2cpg.parser

import io.shiftleft.js2cpg.io.ExternalCommand
import io.shiftleft.js2cpg.preprocessing.TypescriptTranspiler._
import org.slf4j.LoggerFactory
import play.api.libs.json.Json

import java.nio.file.Path
import scala.util.{Failure, Success}

object TsConfigJsonParser {

  private val logger = LoggerFactory.getLogger(TsConfigJsonParser.getClass)

  def module(projectPath: Path, tsc: String): String = {
    ExternalCommand.run(s"$tsc --showConfig", projectPath.toString) match {
      case Success(tsConfig) =>
        val json = Json.parse(tsConfig)
        val moduleOption = (json \ "compilerOptions" \ "module")
          .asOpt[String]
        moduleOption match {
          case Some(module) if module == ESNEXT || module == ES2020 => ES2020
          case _                                                    => COMMONJS
        }
      case Failure(exception) =>
        logger.debug(
          s"\t- TypeScript - acquiring tsconfig.json failed: ${exception.getMessage}. Assuming no tsconfig and proceeding with $COMMONJS defaults.")
        COMMONJS
    }
  }

  def isSolutionTsConfig(projectPath: Path, tsc: String): Boolean = {
    // a solution tsconfig is one with 0 files and at least one reference, see https://angular.io/config/solution-tsconfig
    ExternalCommand.run(s"$tsc --listFilesOnly", projectPath.toString) match {
      case Success(files) =>
        files.isEmpty
      case Failure(exception) =>
        logger.debug(
          s"\t- TypeScript - listing files failed: ${exception.getMessage}. Assuming ${projectPath.toString} is not a solution tsconfig.")
        false
    }
  }

  def subprojects(projectPath: Path, tsc: String): List[String] = {
    ExternalCommand.run(s"$tsc --showConfig", projectPath.toString) match {
      case Success(config) =>
        val json = Json.parse(config)

        (json \ "references")
          .asOpt[List[Map[String, String]]]
          .getOrElse(Nil)
          .flatMap(_.get("path"))

      case Failure(exception) =>
        logger.debug(
          s"\t- TypeScript - listing files failed: ${exception.getMessage}. Assuming no solution tsconfig.")
        Nil
    }
  }

}

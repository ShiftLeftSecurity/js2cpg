package io.shiftleft.js2cpg.parser

import com.fasterxml.jackson.databind.ObjectMapper
import io.shiftleft.js2cpg.io.ExternalCommand
import org.slf4j.LoggerFactory

import java.nio.file.Path
import scala.util.{Failure, Success}
import scala.jdk.CollectionConverters.IteratorHasAsScala

object TsConfigJsonParser {

  private val logger = LoggerFactory.getLogger(TsConfigJsonParser.getClass)

  def isSolutionTsConfig(projectPath: Path, tsc: String): Boolean = {
    // a solution tsconfig is one with 0 files and at least one reference, see https://angular.io/config/solution-tsconfig
    ExternalCommand.run(s"${ExternalCommand.toOSCommand(tsc)} --listFilesOnly", projectPath.toString) match {
      case Success(files) =>
        files.isEmpty
      case Failure(exception) =>
        logger.debug(
          s"\t- TypeScript - listing files failed: ${exception.getMessage}. Assuming ${projectPath.toString} is not a solution tsconfig."
        )
        false
    }
  }

  def subprojects(projectPath: Path, tsc: String): List[String] = {
    ExternalCommand.run(s"${ExternalCommand.toOSCommand(tsc)} --showConfig", projectPath.toString) match {
      case Success(config) =>
        val json        = new ObjectMapper().readTree(config)
        val referenceIt = Option(json.get("references")).map(_.elements().asScala).getOrElse(Iterator.empty)
        referenceIt.flatMap { reference => Option(reference.get("path")).map(_.asText) }.toList
      case Failure(exception) =>
        logger.debug(s"\t- TypeScript - listing files failed: ${exception.getMessage}. Assuming no solution tsconfig.")
        Nil
    }
  }

}

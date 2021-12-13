package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.io.ExternalCommand
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

object TranspilingEnvironment {
  // These are singleton objects because we want to check the environment only once
  // even if multiple transpilers require this specific environment:
  private var isValid: Option[Boolean]         = None
  private var isYarnAvailable: Option[Boolean] = None
  private var isNpmAvailable: Option[Boolean]  = None

  val YARN_INSTALL = "yarn install --prefer-offline --ignore-scripts --legacy-peer-deps"
  val NPM_INSTALL  = "npm install --prefer-offline --no-audit --progress=false --ignore-scripts --legacy-peer-deps"
}

trait TranspilingEnvironment {
  self: Transpiler =>

  import TranspilingEnvironment._

  private val logger = LoggerFactory.getLogger(getClass)

  private def checkForYarn(): Boolean = {
    logger.debug("\t+ Checking yarn ...")
    ExternalCommand.run("yarn -v", projectPath.toString) match {
      case Success(result) =>
        logger.debug(s"\t+ yarn is available: $result")
        true
      case Failure(_) =>
        logger.error("\t- yarn is not installed. Transpiling sources will not be available.")
        false
    }
  }

  private def checkForNpm(): Boolean = {
    logger.debug(s"\t+ Checking npm ...")
    ExternalCommand.run("npm -v", projectPath.toString) match {
      case Success(result) =>
        logger.debug(s"\t+ npm is available: $result")
        true
      case Failure(_) =>
        logger.error("\t- npm is not installed. Transpiling sources will not be available.")
        false
    }
  }

  private def setNpmPython(): Boolean = {
    logger.debug(s"\t+ Setting npm config ...")
    ExternalCommand.run("npm config set python python2.7", projectPath.toString) match {
      case Success(_) =>
        logger.debug(s"\t+ Set successfully")
        true
      case Failure(exception) =>
        logger.debug(s"\t- Failed setting npm config: ${exception.getMessage}")
        false
    }
  }

  protected def nodeVersion(): Option[String] = {
    logger.debug(s"\t+ Checking node ...")
    ExternalCommand.run("node -v", projectPath.toString) match {
      case Success(result) =>
        logger.debug(s"\t+ node is available: $result")
        Some(result)
      case Failure(_) =>
        logger.error("\t- node is not installed.")
        None
    }
  }

  protected def valid(): Boolean = isValid match {
    case Some(value) =>
      value
    case None =>
      isValid = Some((yarnAvailable() || npmAvailable()) && setNpmPython())
      isValid.get
  }

  protected def yarnAvailable(): Boolean = isYarnAvailable match {
    case Some(value) =>
      value
    case None =>
      isYarnAvailable = Some((File(projectPath) / "yarn.lock").exists && checkForYarn())
      isYarnAvailable.get
  }

  protected def npmAvailable(): Boolean = isNpmAvailable match {
    case Some(value) =>
      value
    case None =>
      isNpmAvailable = Some(checkForNpm())
      isNpmAvailable.get
  }

}

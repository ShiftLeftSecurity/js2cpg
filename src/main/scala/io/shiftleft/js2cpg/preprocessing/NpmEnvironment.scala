package io.shiftleft.js2cpg.preprocessing

import io.shiftleft.js2cpg.io.ExternalCommand
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

object NpmEnvironment {
  // This is in a singleton object because we want to check the environment only once
  // even if multiple transpilers require this specific environment.
  private var isValid: Option[Boolean] = None

  val YARN_INSTALL = "yarn install --prefer-offline"
  val NPM_INSTALL  = "npm install --prefer-offline --no-audit --progress=false"
}

trait NpmEnvironment {
  self: Transpiler =>

  import NpmEnvironment._

  private val logger = LoggerFactory.getLogger(getClass)

  private def isNpmAvailable: Boolean = {
    logger.debug(s"\t+ Checking npm ...")
    ExternalCommand.run("npm -v", projectPath.toString) match {
      case Success(result) =>
        logger.debug(s"\t+ npm is available: $result")
        true
      case Failure(_) =>
        logger.debug("\t- npm is not installed. Transpiling sources will not be available.")
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

  protected def valid(): Boolean = isValid match {
    case Some(value) =>
      value
    case None =>
      isValid = Some(isNpmAvailable && setNpmPython())
      isValid.get
  }

}

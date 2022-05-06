package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.io.ExternalCommand
import io.shiftleft.js2cpg.parser.PackageJsonParser
import org.slf4j.LoggerFactory

import java.nio.file.Path
import scala.util.{Failure, Success}

object TranspilingEnvironment {
  // These are singleton objects because we want to check the environment only once
  // even if multiple transpilers require this specific environment:
  private var isValid: Option[Boolean]         = None
  private var isPnpmAvailable: Option[Boolean] = None
  private var isYarnAvailable: Option[Boolean] = None
  private var isNpmAvailable: Option[Boolean]  = None

  val PNPM: String = ExternalCommand.toOSCommand("pnpm")
  val YARN: String = ExternalCommand.toOSCommand("yarn")
  val NPM: String  = ExternalCommand.toOSCommand("npm")

  val YARN_ADD: String =
    s"$YARN --prefer-offline --ignore-scripts --legacy-peer-deps --dev -W add"
  val YARN_INSTALL: String =
    s"$YARN --prefer-offline --ignore-scripts --legacy-peer-deps install"
  val PNPM_ADD: String =
    s"$PNPM --prefer-offline --ignore-scripts add -D"
  val PNPM_INSTALL: String =
    s"$PNPM --prefer-offline --ignore-scripts install"
  val NPM_INSTALL: String =
    s"$NPM --prefer-offline --no-audit --progress=false --ignore-scripts --legacy-peer-deps --save-dev install"
}

trait TranspilingEnvironment {
  self: Transpiler =>

  import TranspilingEnvironment._

  private val logger = LoggerFactory.getLogger(getClass)

  private def checkForPnpm(): Boolean = {
    logger.debug(s"\t+ Checking pnpm ...")
    ExternalCommand.run(s"${TranspilingEnvironment.PNPM} -v", projectPath.toString) match {
      case Success(result) =>
        logger.debug(s"\t+ pnpm is available: $result")
        true
      case Failure(_) =>
        logger.error("\t- pnpm is not installed. Transpiling sources will not be available.")
        false
    }
  }

  private def checkForYarn(): Boolean = {
    logger.debug("\t+ Checking yarn ...")
    ExternalCommand.run(s"${TranspilingEnvironment.YARN} -v", projectPath.toString) match {
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
    ExternalCommand.run(s"${TranspilingEnvironment.NPM} -v", projectPath.toString) match {
      case Success(result) =>
        logger.debug(s"\t+ npm is available: $result")
        true
      case Failure(_) =>
        logger.error("\t- npm is not installed. Transpiling sources will not be available.")
        false
    }
  }

  private def setNpmPython(): Boolean = {
    logger.debug("\t+ Setting npm config ...")
    ExternalCommand.run(s"${TranspilingEnvironment.NPM} config set python python2.7", projectPath.toString) match {
      case Success(_) =>
        logger.debug("\t+ Set successfully")
        true
      case Failure(exception) =>
        logger.debug("\t- Failed setting npm config", exception)
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

  protected def valid(dir: Path): Boolean = isValid match {
    case Some(value) =>
      value
    case None =>
      isValid = Some((pnpmAvailable(dir) || yarnAvailable() || npmAvailable()) && setNpmPython())
      isValid.get
  }

  private def anyLockFileExists(dir: Path, lockFiles: List[String]): Boolean = {
    lockFiles.exists { f =>
      val lockFile = File(dir) / f
      lockFile.exists
    }
  }

  protected def pnpmAvailable(dir: Path): Boolean = isPnpmAvailable match {
    case Some(value) =>
      val hasLockFile = anyLockFileExists(
        dir,
        List(PackageJsonParser.PACKAGE_PNPM_LOCK_FILENAME_BAK, PackageJsonParser.PACKAGE_PNPM_LOCK_FILENAME)
      )
      value && hasLockFile
    case None =>
      val hasLockFile = anyLockFileExists(
        dir,
        List(PackageJsonParser.PACKAGE_PNPM_LOCK_FILENAME_BAK, PackageJsonParser.PACKAGE_PNPM_LOCK_FILENAME)
      )
      isPnpmAvailable = Some(hasLockFile && checkForPnpm())
      isPnpmAvailable.get
  }

  protected def yarnAvailable(): Boolean = isYarnAvailable match {
    case Some(value) =>
      value
    case None =>
      val hasLockFile = anyLockFileExists(
        projectPath,
        List(PackageJsonParser.PACKAGE_YARN_LOCK_FILENAME_BAK, PackageJsonParser.PACKAGE_YARN_LOCK_FILENAME)
      )
      isYarnAvailable = Some(hasLockFile && checkForYarn())
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

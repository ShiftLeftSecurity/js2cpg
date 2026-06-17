package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.io.ExternalCommand
import io.shiftleft.js2cpg.io.FileDefaults
import org.slf4j.LoggerFactory

import java.nio.file.Path
import scala.util.{Failure, Success}

object TranspilingEnvironment {

  val ENV_PATH_CONTENT: String = scala.util.Properties.envOrElse("PATH", "")

  // These are singleton objects because we want to check the environment only once
  // even if multiple transpilers require this specific environment:
  private var isValid: Option[Boolean]         = None
  private var isPnpmAvailable: Option[Boolean] = None
  private var isYarnAvailable: Option[Boolean] = None
  private var isNpmAvailable: Option[Boolean]  = None

  private val PNPM: String = ExternalCommand.toOSCommand("pnpm")
  private val YARN: String = ExternalCommand.toOSCommand("yarn")
  private val NPM: String  = ExternalCommand.toOSCommand("npm")

  val YARN_ADD: String =
    s"$YARN --ignore-scripts --legacy-peer-deps --dev -W add"
  val YARN_INSTALL: String =
    s"$YARN --ignore-scripts --legacy-peer-deps install"
  val PNPM_ADD: String =
    s"$PNPM --ignore-scripts add -D"
  val PNPM_INSTALL: String =
    s"$PNPM --ignore-scripts install"
  val NPM_INSTALL: String =
    s"$NPM --no-audit --progress=false --ignore-scripts --legacy-peer-deps --save-dev install"

}

trait TranspilingEnvironment {
  self: Transpiler =>

  import TranspilingEnvironment.*

  private val logger = LoggerFactory.getLogger(getClass)

  object Versions {
    val babelVersions: Map[String, String] = Map(
      "@babel/core"                                         -> "8.0.0",
      "@babel/cli"                                          -> "8.0.0",
      "@babel/preset-env"                                   -> "8.0.0",
      "@babel/preset-flow"                                  -> "8.0.0",
      "@babel/preset-react"                                 -> "8.0.0",
      "@babel/preset-typescript"                            -> "8.0.0",
      "@babel/plugin-transform-class-properties"            -> "8.0.0",
      "@babel/plugin-transform-private-methods"             -> "8.0.0",
      "@babel/plugin-transform-private-property-in-object"  -> "8.0.0",
      "@babel/plugin-transform-object-rest-spread"          -> "8.0.0",
      "@babel/plugin-transform-nullish-coalescing-operator" -> "8.0.0",
      "@babel/plugin-transform-runtime"                     -> "8.0.0"
    )

    private val versions: Map[String, String] =
      babelVersions ++ Map("pug-cli" -> "1.0.0-alpha6", "typescript" -> "6.0.3", "@vue/cli-service-global" -> "4.5.19")

    def nameAndVersion(dependencyName: String): String = {
      if (config.fixedTranspilationDependencies) {
        val version = versions.get(dependencyName).map(v => s"@$v").getOrElse("")
        s"$dependencyName$version"
      } else {
        dependencyName
      }
    }
  }

  private def checkForPnpm(): Boolean = {
    logger.debug(s"\t+ Checking pnpm ...")
    ExternalCommand.run(s"${TranspilingEnvironment.PNPM} -v", projectPath.toString) match {
      case Success(result) =>
        logger.debug(s"\t+ pnpm is available: $result")
        true
      case Failure(_) =>
        logger.debug("\t- pnpm is not installed.")
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
        logger.debug("\t- yarn is not installed.")
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
      nodeVersion()
      isValid = Some(pnpmAvailable(dir) || yarnAvailable() || npmAvailable())
      logger.debug(s"\t+ additional node ENV settings: '${NODE_OPTIONS.mkString}'")
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
      val hasLockFile =
        anyLockFileExists(dir, List(FileDefaults.PNPM_LOCK_FILENAME_BAK, FileDefaults.PNPM_LOCK_FILENAME))
      value && hasLockFile
    case None =>
      val hasLockFile =
        anyLockFileExists(dir, List(FileDefaults.PNPM_LOCK_FILENAME_BAK, FileDefaults.PNPM_LOCK_FILENAME))
      isPnpmAvailable = Some(hasLockFile && checkForPnpm())
      isPnpmAvailable.get
  }

  protected def yarnAvailable(): Boolean = isYarnAvailable match {
    case Some(value) =>
      value
    case None =>
      val hasLockFile =
        anyLockFileExists(projectPath, List(FileDefaults.YARN_LOCK_FILENAME_BAK, FileDefaults.YARN_LOCK_FILENAME))
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

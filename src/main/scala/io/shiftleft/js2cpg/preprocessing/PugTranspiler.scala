package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.PUG_SUFFIX
import io.shiftleft.js2cpg.io.{ExternalCommand, FileUtils}
import io.shiftleft.js2cpg.preprocessing.TranspilingEnvironment.Versions
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success}

class PugTranspiler(override val config: Config, override val projectPath: Path) extends Transpiler {

  private val logger        = LoggerFactory.getLogger(getClass)
  private val pug           = Paths.get(projectPath.toString, "node_modules", ".bin", "pug").toString
  private val pugAndVersion = Versions.nameAndVersion("pug-cli")

  private def hasPugFiles: Boolean =
    FileUtils.getFileTree(projectPath, config, List(PUG_SUFFIX)).nonEmpty

  override def shouldRun(): Boolean = config.templateTranspiling && hasPugFiles

  private def installPugPlugins(): Boolean = {
    val command = if (pnpmAvailable(projectPath)) {
      s"${TranspilingEnvironment.PNPM_ADD} $pugAndVersion && ${TranspilingEnvironment.PNPM_INSTALL}"
    } else if (yarnAvailable()) {
      s"${TranspilingEnvironment.YARN_ADD} $pugAndVersion && ${TranspilingEnvironment.YARN_INSTALL}"
    } else {
      s"${TranspilingEnvironment.NPM_INSTALL} $pugAndVersion"
    }
    logger.info("Installing Pug dependencies and plugins. That will take a while.")
    logger.debug(s"\t+ Installing Pug plugins with command '$command' in path '$projectPath'")
    ExternalCommand.run(command, projectPath.toString) match {
      case Success(_) =>
        logger.info("\t+ Pug plugins installed")
        true
      case Failure(exception) =>
        logger.warn("\t- Failed to install Pug plugins", exception)
        false
    }
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    if (installPugPlugins()) {
      val command = s"${ExternalCommand.toOSCommand(pug)} --client --no-debug --out $tmpTranspileDir ."
      logger.debug(s"\t+ transpiling Pug templates in $projectPath to $tmpTranspileDir")
      ExternalCommand.run(command, projectPath.toString) match {
        case Success(_) =>
          logger.debug("\t+ transpiling Pug templates finished")
        case Failure(exception) =>
          logger.debug("\t- transpiling Pug templates failed", exception)
      }
    }
    true
  }

  override def validEnvironment(): Boolean = valid(projectPath)

  override protected def logExecution(): Unit =
    logger.info(s"PUG - transpiling source files in '${File(projectPath).name}'")
}

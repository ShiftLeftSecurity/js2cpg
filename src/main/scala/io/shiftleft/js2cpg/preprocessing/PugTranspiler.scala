package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.PUG_SUFFIX
import io.shiftleft.js2cpg.io.{ExternalCommand, FileUtils}
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success}

class PugTranspiler(override val config: Config, override val projectPath: Path)
    extends Transpiler
    with NpmEnvironment {

  private val logger = LoggerFactory.getLogger(getClass)

  private def hasPugFiles: Boolean =
    FileUtils.getFileTree(projectPath, config, List(PUG_SUFFIX)).nonEmpty

  override def shouldRun(): Boolean = config.templateTranspiling && hasPugFiles

  private def installPugPlugins(): Boolean = {
    val command = if ((File(projectPath) / "yarn.lock").exists) {
      s"yarn add pug-cli --dev && ${NpmEnvironment.YARN_INSTALL}"
    } else {
      s"npm install --save-dev pug-cli && ${NpmEnvironment.NPM_INSTALL}"
    }
    logger.debug(s"\t+ Installing Pug plugins ...")
    ExternalCommand.run(command, projectPath.toString) match {
      case Success(_) =>
        logger.debug(s"\t+ Pug plugins installed")
        true
      case Failure(exception) =>
        logger.debug(s"\t- Failed to install Pug plugins: ${exception.getMessage}")
        false
    }
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    if (installPugPlugins()) {
      val pug     = Paths.get(projectPath.toString, "node_modules", ".bin", "pug")
      val command = s"$pug --client --no-debug --out $tmpTranspileDir ."
      logger.debug(s"\t+ transpiling Pug templates in $projectPath to $tmpTranspileDir")
      ExternalCommand.run(command, projectPath.toString) match {
        case Success(result) =>
          logger.debug(s"\t+ transpiling Pug templates finished. $result")
        case Failure(exception) =>
          logger.debug(s"\t- transpiling Pug templates failed: ${exception.getMessage}")
      }
    }
    true
  }

  override def validEnvironment(): Boolean = valid()

  override protected def logExecution(): Unit =
    logger.info(s"PUG - transpiling source files in '${File(projectPath).name}'")
}

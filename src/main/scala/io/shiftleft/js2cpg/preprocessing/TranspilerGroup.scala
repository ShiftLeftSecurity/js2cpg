package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.ExternalCommand
import org.slf4j.LoggerFactory

import java.nio.file.Path
import scala.util.{Failure, Success}

class TranspilerGroup(override val config: Config,
                      override val projectPath: Path,
                      transpilers: Seq[Transpiler])
    extends Transpiler {

  private val logger = LoggerFactory.getLogger(getClass)

  private val BABEL_PLUGINS: String =
    "@babel/core " +
      "@babel/cli " +
      "@babel/preset-env " +
      "@babel/preset-flow " +
      "@babel/preset-react " +
      "@babel/preset-typescript " +
      "@babel/plugin-proposal-class-properties " +
      "@babel/plugin-proposal-private-methods " +
      "@babel/plugin-proposal-object-rest-spread " +
      "@babel/plugin-proposal-nullish-coalescing-operator " +
      "@babel/plugin-transform-property-mutators"

  private def isYarnAvailable: Boolean = {
    logger.debug(s"\t+ Checking yarn ...")
    ExternalCommand.run("yarn -v", projectPath.toString) match {
      case Success(result) =>
        logger.debug(s"\t+ yarn is available: $result")
        true
      case Failure(_) =>
        logger.debug("\t- yarn is not installed. Transpiling sources will not be available.")
        false
    }
  }

  private def installPlugins(): Boolean = {
    val command = if ((File(projectPath) / "yarn.lock").exists && isYarnAvailable) {
      s"yarn add $BABEL_PLUGINS --dev -W && ${NpmEnvironment.YARN_INSTALL}"
    } else {
      s"npm install --save-dev $BABEL_PLUGINS && ${NpmEnvironment.NPM_INSTALL}"
    }
    logger.debug(s"\t+ Installing plugins ...")
    ExternalCommand.run(command, projectPath.toString) match {
      case Success(_) =>
        logger.debug(s"\t+ Plugins installed")
        true
      case Failure(exception) =>
        logger.debug(s"\t- Failed to install plugins: ${exception.getMessage}")
        false
    }
  }

  override def shouldRun(): Boolean = transpilers.exists(_.shouldRun())

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    if (installPlugins()) {
      transpilers.takeWhile(_.run(tmpTranspileDir)).length == transpilers.length
    } else {
      true
    }
  }

  override def validEnvironment(): Boolean = transpilers.forall(_.validEnvironment())

  override protected def logExecution(): Unit = {
    logger.info(s"Downloading / installing plugins in '${File(projectPath).name}'")
  }
}

package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.ExternalCommand
import org.slf4j.LoggerFactory

import java.nio.file.Path
import scala.util.{Failure, Success}

case class TranspilerGroup(override val config: Config,
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

  private def installPlugins(): Boolean = {
    val command = if (yarnAvailable()) {
      s"yarn add $BABEL_PLUGINS --dev -W --legacy-peer-deps && ${TranspilingEnvironment.YARN_INSTALL}"
    } else {
      s"npm install --save-dev $BABEL_PLUGINS --legacy-peer-deps && ${TranspilingEnvironment.NPM_INSTALL}"
    }
    logger.info("Installing project dependencies and plugins. This might take a while.")
    logger.debug(s"\t+ Installing plugins with command '$command' in path '$projectPath'")
    ExternalCommand.run(command, projectPath.toString) match {
      case Success(_) =>
        logger.debug("\t+ Plugins installed")
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

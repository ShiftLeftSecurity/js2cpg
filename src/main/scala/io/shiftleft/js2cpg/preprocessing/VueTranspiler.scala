package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.ExternalCommand
import io.shiftleft.js2cpg.io.FileDefaults.VUE_SUFFIX
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.js2cpg.parser.PackageJsonParser
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success}

object VueTranspiler {

  private def hasVueFiles(config: Config, projectPath: Path): Boolean =
    FileUtils.getFileTree(projectPath, config, List(VUE_SUFFIX)).nonEmpty

  def isVueProject(config: Config, projectPath: Path): Boolean = {
    val hasVueDep =
      PackageJsonParser
        .dependencies((File(projectPath) / PackageJsonParser.PACKAGE_JSON_FILENAME).path)
        .contains("vue")
    hasVueDep || hasVueFiles(config, projectPath)
  }
}

class VueTranspiler(override val config: Config, override val projectPath: Path)
    extends Transpiler {

  import VueTranspiler.isVueProject

  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val NODE_OPTIONS: Map[String, String] = nodeOptions()

  override def shouldRun(): Boolean = config.vueTranspiling && isVueProject(config, projectPath)

  private def nodeOptions(): Map[String, String] = {
    // TODO: keep this until https://github.com/webpack/webpack/issues/14532 is fixed
    if (nodeVersion().exists(_.startsWith("v17"))) {
      Map("NODE_OPTIONS" -> "--openssl-legacy-provider")
    } else {
      Map.empty
    }
  }

  private def installVuePlugins(): Boolean = {
    val command = if (yarnAvailable()) {
      s"yarn add @vue/cli-service-global --dev --legacy-peer-deps && ${TranspilingEnvironment.YARN_INSTALL}"
    } else {
      s"npm install --save-dev @vue/cli-service-global --legacy-peer-deps && ${TranspilingEnvironment.NPM_INSTALL}"
    }
    logger.info("Installing Vue.js dependencies and plugins. That will take a while.")
    logger.debug(s"\t+ Installing Vue.js plugins with command '$command' in path '$projectPath'")
    ExternalCommand.run(command, projectPath.toString, extraEnv = NODE_OPTIONS) match {
      case Success(_) =>
        logger.info("\t+ Vue.js plugins installed")
        true
      case Failure(exception) =>
        logger.error("\t- Failed to install Vue.js plugins", exception)
        false
    }
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    if (installVuePlugins()) {
      val vue     = Paths.get(projectPath.toString, "node_modules", ".bin", "vue-cli-service")
      val command = s"$vue build --dest $tmpTranspileDir --mode development --no-clean"
      logger.debug(s"\t+ Vue.js transpiling $projectPath to $tmpTranspileDir")
      ExternalCommand.run(command, projectPath.toString, extraEnv = NODE_OPTIONS) match {
        case Success(result) =>
          logger.debug("\t+ Vue.js transpiling finished")
        case Failure(exception) =>
          logger.debug("\t- Vue.js transpiling failed", exception)
      }
    }
    true
  }

  override def validEnvironment(): Boolean = valid()

  override protected def logExecution(): Unit =
    logger.info(s"Vue.js - transpiling source files in '${File(projectPath).name}'")

}

package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.VUE_SUFFIX
import io.shiftleft.js2cpg.io.{ExternalCommand, FileUtils}
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success}

class VueTranspiler(override val config: Config, override val projectPath: Path)
    extends Transpiler
    with NpmEnvironment {

  private val logger = LoggerFactory.getLogger(getClass)

  override def shouldRun(): Boolean = config.vueTranspiling && isVueProject

  private def installVuePlugins(): Boolean = {
    val command = if ((File(projectPath) / "yarn.lock").exists) {
      s"yarn add @vue/cli-service-global --dev && ${NpmEnvironment.YARN_INSTALL}"
    } else {
      s"npm install --save-dev @vue/cli-service-global && ${NpmEnvironment.NPM_INSTALL}"
    }
    logger.debug(s"\t+ Installing Vue.js plugins ...")
    ExternalCommand.run(command, projectPath.toString) match {
      case Success(_) =>
        logger.debug(s"\t+ Vue.js plugins installed")
        true
      case Failure(exception) =>
        logger.debug(s"\t- Failed to install Vue.js plugins: ${exception.getMessage}")
        false
    }
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    if (installVuePlugins()) {
      val vue     = Paths.get(projectPath.toString, "node_modules", ".bin", "vue-cli-service")
      val command = s"$vue build --dest $tmpTranspileDir --mode development --no-clean"
      logger.debug(s"\t+ Vue.js transpiling $projectPath to $tmpTranspileDir")
      ExternalCommand.run(command, projectPath.toString) match {
        case Success(result) =>
          logger.debug(s"\t+ Vue.js transpiling finished. $result")
        case Failure(exception) =>
          logger.debug(s"\t- Vue.js transpiling failed: ${exception.getMessage}")
      }
    }
    true
  }

  override def validEnvironment(): Boolean = valid()

  override protected def logExecution(): Unit =
    logger.info(s"Vue.js - transpiling source files in '${File(projectPath).name}'")

}

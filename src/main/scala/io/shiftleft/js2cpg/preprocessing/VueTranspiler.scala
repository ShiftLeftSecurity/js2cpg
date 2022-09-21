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
        .dependencies((File(config.srcDir) / PackageJsonParser.PACKAGE_JSON_FILENAME).path)
        .contains("vue")
    hasVueDep && hasVueFiles(config, projectPath)
  }
}

class VueTranspiler(override val config: Config, override val projectPath: Path) extends Transpiler {

  import VueTranspiler.isVueProject

  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val NODE_OPTIONS: Map[String, String] = nodeOptions()

  private val vue = Paths.get(projectPath.toString, "node_modules", ".bin", "vue-cli-service").toString

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
    val command = if (pnpmAvailable(projectPath)) {
      s"${TranspilingEnvironment.PNPM_ADD} @vue/cli-service-global && ${TranspilingEnvironment.PNPM_INSTALL}"
    } else if (yarnAvailable()) {
      s"${TranspilingEnvironment.YARN_ADD} @vue/cli-service-global && ${TranspilingEnvironment.YARN_INSTALL}"
    } else {
      s"${TranspilingEnvironment.NPM_INSTALL} @vue/cli-service-global && ${TranspilingEnvironment.NPM_INSTALL}"
    }
    logger.info("Installing Vue.js dependencies and plugins. That will take a while.")
    logger.debug(s"\t+ Installing Vue.js plugins with command '$command' in path '$projectPath'")
    ExternalCommand.run(command, projectPath.toString, extraEnv = NODE_OPTIONS) match {
      case Success(_) =>
        logger.info("\t+ Vue.js plugins installed")
        true
      case Failure(exception) =>
        logger.warn("\t- Failed to install Vue.js plugins", exception)
        false
    }
  }

  private def createCustomBrowserslistFile(): Unit = {
    val browserslistFile = File(projectPath) / ".browserslistrc"
    if (browserslistFile.exists) {
      browserslistFile.delete(swallowIOExceptions = true)
    }
    val customBrowserslistFile = File
      .newTemporaryFile(".browserslistrc", parent = Some(projectPath))
      .deleteOnExit(swallowIOExceptions = true)
    customBrowserslistFile.writeText("last 2 years")
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    if (installVuePlugins()) {
      createCustomBrowserslistFile()
      val command = s"${ExternalCommand.toOSCommand(vue)} build --dest $tmpTranspileDir --mode development --no-clean"
      logger.debug(s"\t+ Vue.js transpiling $projectPath to $tmpTranspileDir")
      ExternalCommand.run(command, projectPath.toString, extraEnv = NODE_OPTIONS) match {
        case Success(_)         => logger.debug("\t+ Vue.js transpiling finished")
        case Failure(exception) => logger.debug("\t- Vue.js transpiling failed", exception)
      }
    }
    true
  }

  override def validEnvironment(): Boolean = valid(projectPath)

  override protected def logExecution(): Unit =
    logger.info(s"Vue.js - transpiling source files in '${File(projectPath).name}'")

}

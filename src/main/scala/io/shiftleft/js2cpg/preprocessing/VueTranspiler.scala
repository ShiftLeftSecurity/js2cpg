package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.ExternalCommand
import io.shiftleft.js2cpg.io.FileDefaults
import io.shiftleft.js2cpg.io.FileDefaults.VUE_SUFFIX
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.js2cpg.parser.PackageJsonParser
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success}
import scala.util.Try

object VueTranspiler {

  private val BrowsersListRc: String = ".browserslistrc"
  private val VueConfigJs: String    = "vue.config.js"
  private val Vue2Config: String =
    """
      |module.exports = {
      |    configureWebpack: {
      |        devtool: 'source-map'
      |    }
      |}
      |""".stripMargin
  private val Vue3Config: String =
    """
      |const { defineConfig } = require('@vue/cli-service');
      |module.exports = defineConfig({
      |  configureWebpack: {
      |    devtool: 'source-map',
      |  }
      |});
      |""".stripMargin

  private def hasVueFiles(config: Config, projectPath: Path): Boolean =
    FileUtils.getFileTree(projectPath, config, List(VUE_SUFFIX)).nonEmpty

  def isVueProject(config: Config, projectPath: Path): Boolean = {
    val hasVueDep =
      PackageJsonParser.dependencies((File(config.srcDir) / FileDefaults.PACKAGE_JSON_FILENAME).path).contains("vue")
    hasVueDep && hasVueFiles(config, projectPath)
  }

  private def vueVersion(config: Config): Int = {
    val versionString =
      PackageJsonParser.dependencies((File(config.srcDir) / FileDefaults.PACKAGE_JSON_FILENAME).path)("vue")
    // ignore ~, ^, and more from semver; see: https://stackoverflow.com/a/25861938
    val c = versionString.collectFirst { case c if Try(c.toString.toInt).isSuccess => c.toString.toInt }
    c.getOrElse(3) // 3 is the latest version; we default to that
  }
}

class VueTranspiler(override val config: Config, override val projectPath: Path) extends Transpiler {

  import VueTranspiler._

  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val VUE_NODE_OPTIONS: Map[String, String] = nodeOptions()

  private val vue           = Paths.get(projectPath.toString, "node_modules", ".bin", "vue-cli-service").toString
  private val vueAndVersion = Versions.nameAndVersion("@vue/cli-service-global")

  override def shouldRun(): Boolean = config.vueTranspiling && isVueProject(config, projectPath)

  private def nodeOptions(): Map[String, String] = {
    // TODO: keep this until https://github.com/webpack/webpack/issues/14532 is fixed.
    if (nodeVersion().exists(v => v.startsWith("v17") || v.startsWith("v18") || v.startsWith("v19"))) {
      Map("NODE_OPTIONS" -> "--openssl-legacy-provider")
    } else {
      Map.empty
    }
  }

  private def installVuePlugins(): Boolean = {
    val command = if (pnpmAvailable(projectPath)) {
      s"${TranspilingEnvironment.PNPM_ADD} $vueAndVersion && ${TranspilingEnvironment.PNPM_INSTALL}"
    } else if (yarnAvailable()) {
      s"${TranspilingEnvironment.YARN_ADD} $vueAndVersion && ${TranspilingEnvironment.YARN_INSTALL}"
    } else {
      s"${TranspilingEnvironment.NPM_INSTALL} $vueAndVersion"
    }
    logger.info("Installing Vue.js dependencies and plugins. That will take a while.")
    logger.debug(s"\t+ Installing Vue.js plugins with command '$command' in path '$projectPath'")
    ExternalCommand.run(command, projectPath.toString, extraEnv = VUE_NODE_OPTIONS) match {
      case Success(_) =>
        logger.info("\t+ Vue.js plugins installed")
        true
      case Failure(exception) =>
        logger.warn("\t- Failed to install Vue.js plugins", exception)
        false
    }
  }

  private def createCustomBrowserslistFile(): Unit = {
    val browserslistFile = File(projectPath) / BrowsersListRc
    browserslistFile.delete(swallowIOExceptions = true)
    browserslistFile.createFile().deleteOnExit(swallowIOExceptions = true)
    browserslistFile.writeText("last 2 years")
  }

  private def createCustomVueConfigFile(): Unit = {
    val vueConfigJsFile = File(projectPath) / VueConfigJs
    vueConfigJsFile.delete(swallowIOExceptions = true)
    vueConfigJsFile.createFile().deleteOnExit(swallowIOExceptions = true)
    if (vueVersion(config) <= 2) {
      vueConfigJsFile.writeText(Vue2Config)
    } else {
      vueConfigJsFile.writeText(Vue3Config)
    }
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    if (installVuePlugins()) {
      createCustomBrowserslistFile()
      createCustomVueConfigFile()
      val command =
        s"${ExternalCommand.toOSCommand(vue)} build --dest '$tmpTranspileDir' --mode development --no-clean --modern"
      logger.debug(s"\t+ Vue.js transpiling $projectPath to '$tmpTranspileDir'")
      ExternalCommand.run(command, projectPath.toString, extraEnv = VUE_NODE_OPTIONS) match {
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

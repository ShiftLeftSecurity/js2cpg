package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.JS_SUFFIX
import io.shiftleft.js2cpg.io.{ExternalCommand, FileUtils}
import io.shiftleft.js2cpg.parser.PackageJsonParser
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success}

object NuxtTranspiler {
  val NUXT_FOLDER: String = ".nuxt"

  private var wasExecuted = false

  def remapPath(path: String): String = {
    val newPath = path.replace(NUXT_FOLDER + java.io.File.separator, "").replaceAll(".js$", ".vue")
    if (File(newPath).exists) {
      newPath
    } else {
      path
    }
  }

  def collectJsFiles(dir: Path, config: Config): List[(Path, Path)] = {
    if (wasExecuted) {
      val nuxtFolder     = dir.resolve(NUXT_FOLDER)
      val nuxtDistFolder = nuxtFolder.resolve("dist")
      FileUtils
        .getFileTree(
          nuxtFolder,
          config.copy(ignoredFiles = config.ignoredFiles :+ nuxtDistFolder),
          List(JS_SUFFIX)
        )
        .map(f => (f, dir))
    } else { Nil }
  }
}

class NuxtTranspiler(override val config: Config, override val projectPath: Path)
    extends Transpiler {

  import NuxtTranspiler._

  private val logger = LoggerFactory.getLogger(getClass)

  private def isNuxtProject: Boolean =
    PackageJsonParser
      .dependencies((File(projectPath) / PackageJsonParser.PACKAGE_JSON_FILENAME).path)
      .contains("nuxt")

  override def shouldRun(): Boolean = config.nuxtTranspiling && isNuxtProject

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    val nuxt    = Paths.get(projectPath.toString, "node_modules", ".bin", "nuxt").toString
    val command = s"${ExternalCommand.toOSCommand(nuxt)} --force-exit"
    logger.debug(s"\t+ Nuxt.js transpiling $projectPath")
    ExternalCommand.run(command, projectPath.toString) match {
      case Success(_) =>
        logger.debug("\t+ Nuxt.js transpiling finished")
        new BabelTranspiler(config, projectPath, inDir = Some(Paths.get(NUXT_FOLDER)))
          .run(projectPath.resolve(NUXT_FOLDER))
        wasExecuted = true
      case Failure(exception) =>
        logger.debug("\t- Nuxt.js transpiling failed", exception)
    }
    // we never want other transpilers down the chain to be executed
    false
  }

  override def validEnvironment(): Boolean = valid()

  override protected def logExecution(): Unit =
    logger.info(s"Nuxt.js - transpiling source files in '${File(projectPath).name}'")

}

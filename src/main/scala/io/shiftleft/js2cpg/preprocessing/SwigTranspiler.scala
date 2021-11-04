package io.shiftleft.js2cpg.preprocessing

import better.files.File
import com.atlassian.sourcemap.WritableSourceMapImpl.Builder
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.HTML_SUFFIX
import io.shiftleft.js2cpg.io.{ExternalCommand, FileDefaults, FileUtils}
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success, Using}

class SwigTranspiler(override val config: Config, override val projectPath: Path)
    extends Transpiler
    with NpmEnvironment {

  private val SWIG_MARKERS = List("{%", "{{")

  private val logger = LoggerFactory.getLogger(getClass)

  private val allSwigFiles: List[Path] = findSwigFiles()

  private def findSwigFiles(): List[Path] =
    FileUtils.getFileTree(projectPath, config, HTML_SUFFIX).filter { path =>
      Using(FileUtils.bufferedSourceFromFile(path)) { bufferedSource =>
        bufferedSource.getLines().exists(line => SWIG_MARKERS.exists(line.contains))
      }.getOrElse(false)
    }

  private def hasSwigFiles: Boolean = allSwigFiles.nonEmpty

  override def shouldRun(): Boolean = config.templateTranspiling && hasSwigFiles

  private def installSwigPlugins(): Boolean = {
    val command = if ((File(projectPath) / "yarn.lock").exists) {
      s"yarn add swig --dev && ${NpmEnvironment.YARN_INSTALL}"
    } else {
      s"npm install --save-dev swig && ${NpmEnvironment.NPM_INSTALL}"
    }
    logger.debug(s"\t+ Installing Swig plugins ...")
    ExternalCommand.run(command, projectPath.toString) match {
      case Success(_) =>
        logger.debug(s"\t+ Swig plugins installed")
        true
      case Failure(exception) =>
        logger.debug(s"\t- Failed to install Swig plugins: ${exception.getMessage}")
        false
    }
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    if (installSwigPlugins()) {
      val swig = Paths.get(projectPath.toString, "node_modules", ".bin", "swig")
      allSwigFiles.foreach { swigFile =>
        val outFile =
          File(tmpTranspileDir.toString,
               projectPath
                 .relativize(swigFile)
                 .toString
                 .stripSuffix(FileDefaults.HTML_SUFFIX) + FileDefaults.JS_SUFFIX)
        val command = s"$swig compile $swigFile"
        logger.debug(s"\t+ transpiling Swig template $swigFile")
        ExternalCommand.run(command, projectPath.toString) match {
          case Success(result) =>
            logger.debug(s"\t+ transpiling Swig template finished.")
            outFile.createIfNotExists(createParents = true)
            outFile.writeText(result)
            val sourceMap =
              new Builder()
                .withSources(java.util.Collections.singletonList(swigFile.toString))
                .build()
            val sourceMapFile = File(outFile.parent, outFile.name + ".map")
            sourceMapFile.writeText(sourceMap.generate())
          case Failure(exception) =>
            logger.debug(s"\t- transpiling Swig template failed: ${exception.getMessage}")
        }
      }
    }
    true
  }

  override def validEnvironment(): Boolean = valid()

  override protected def logExecution(): Unit =
    logger.info(s"Swig - transpiling source files in '${File(projectPath).name}'")
}

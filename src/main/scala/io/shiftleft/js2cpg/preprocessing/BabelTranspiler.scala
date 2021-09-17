package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.NODE_MODULES_DIR_NAME
import io.shiftleft.js2cpg.io.ExternalCommand
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success}

class BabelTranspiler(override val config: Config,
                      override val projectPath: Path,
                      subDir: Option[Path] = None,
                      inDir: Option[Path] = None)
    extends Transpiler
    with NpmEnvironment {

  private val logger = LoggerFactory.getLogger(getClass)

  override def shouldRun(): Boolean = config.babelTranspiling && !isVueProject

  private def constructIgnoreDirArgs: String = {
    val ignores = if (config.ignoreTests) {
      NODE_MODULES_DIR_NAME +: (DEFAULT_IGNORED_DIRS ++ DEFAULT_IGNORED_TEST_DIRS)
    } else {
      NODE_MODULES_DIR_NAME +: DEFAULT_IGNORED_DIRS
    }
    ignores.map(dir => s"--ignore $dir").mkString(" ")
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    val in = inDir.map(dir => projectPath.resolve(dir)).getOrElse(projectPath)
    val out =
      subDir
        .map(dir => Paths.get(tmpTranspileDir.toString, dir.toString))
        .getOrElse(tmpTranspileDir)
    val babel = Paths.get(projectPath.toString, "node_modules", ".bin", "babel")
    val command = s"$babel . " +
      "--no-babelrc " +
      "--source-maps true " +
      "--presets @babel/preset-env " +
      "--presets @babel/preset-react " +
      "--presets @babel/preset-flow " +
      "--presets @babel/preset-typescript " +
      "--plugins @babel/plugin-proposal-class-properties " +
      "--plugins @babel/plugin-proposal-private-methods " +
      "--plugins @babel/plugin-proposal-object-rest-spread " +
      "--plugins @babel/plugin-proposal-nullish-coalescing-operator " +
      "--plugins @babel/plugin-transform-property-mutators " +
      s"--out-dir $out $constructIgnoreDirArgs"
    logger.debug(s"\t+ Babel transpiling $projectPath to $out")
    ExternalCommand.run(command, in.toString) match {
      case Success(result) =>
        logger.debug(s"\t+ Babel transpiling finished. $result")
      case Failure(exception) =>
        logger.debug(s"\t- Babel transpiling failed: ${exception.getMessage}")
    }
    true
  }

  override def validEnvironment(): Boolean = valid()

  override protected def logExecution(): Unit =
    logger.info(s"Babel - transpiling source files in '${File(projectPath).name}'")

}

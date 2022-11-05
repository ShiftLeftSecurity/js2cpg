package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.ExternalCommand
import io.shiftleft.js2cpg.io.FileDefaults.NODE_MODULES_DIR_NAME
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success}

class BabelTranspiler(
  override val config: Config,
  override val projectPath: Path,
  subDir: Option[Path] = None,
  inDir: Option[Path] = None
) extends Transpiler {

  private val logger = LoggerFactory.getLogger(getClass)
  private val babel  = Paths.get(projectPath.toString, "node_modules", ".bin", "babel").toString

  override def shouldRun(): Boolean =
    config.babelTranspiling && !VueTranspiler.isVueProject(config, projectPath)

  private def constructIgnoreDirArgs: String = {
    val ignores = if (config.ignoreTests) {
      NODE_MODULES_DIR_NAME +: (DEFAULT_IGNORED_DIRS ++ DEFAULT_IGNORED_TEST_DIRS)
    } else {
      NODE_MODULES_DIR_NAME +: DEFAULT_IGNORED_DIRS
    }
    s"--ignore '${ignores.map(dir => s"**/$dir").mkString(",")}'"
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    val in = inDir.map(dir => projectPath.resolve(dir)).getOrElse(projectPath)
    val outDir =
      subDir.map(s => File(tmpTranspileDir.toString, s.toString)).getOrElse(File(tmpTranspileDir))

    val command = s"${ExternalCommand.toOSCommand(babel)} . " +
      "--no-babelrc " +
      s"--source-root '${in.toString}' " +
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
      "--plugins @babel/plugin-transform-runtime " +
      s"--out-dir $outDir $constructIgnoreDirArgs"
    logger.debug(s"\t+ Babel transpiling $projectPath to $outDir with command '$command'")
    ExternalCommand.run(command, in.toString) match {
      case Success(_)         => logger.debug("\t+ Babel transpiling finished")
      case Failure(exception) => logger.debug("\t- Babel transpiling failed", exception)
    }
    true
  }

  override def validEnvironment(): Boolean = valid(inDir.map(dir => projectPath.resolve(dir)).getOrElse(projectPath))

  override protected def logExecution(): Unit =
    logger.info(s"Babel - transpiling source files in '${File(projectPath).name}'")

}

package io.shiftleft.js2cpg.preprocessing

import better.files.File
import better.files.File.LinkOptions
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults
import io.shiftleft.js2cpg.io.FileDefaults._
import io.shiftleft.js2cpg.io.FileUtils
import org.slf4j.LoggerFactory

import java.nio.file.{Path, StandardCopyOption}
import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps

class TranspilationRunner(
  projectPath: Path,
  tmpTranspileDir: Path,
  config: Config,
  subDir: Option[Path] = None
) {

  private val logger = LoggerFactory.getLogger(getClass)

  private val transpilers: Seq[Transpiler] = createTranspilers()

  private def createTranspilers(): Seq[Transpiler] = {
    // We always run the following transpilers by default when not stated otherwise in the Config.
    // This includes running them for sub-projects.
    val baseTranspilers = TranspilerGroup(
      config,
      projectPath,
      Seq(
        new TypescriptTranspiler(config, projectPath, subDir = subDir),
        new BabelTranspiler(config, projectPath, subDir = subDir)
      )
    )

    // When we got no sub-project, we also run the following ones:
    if (subDir.isEmpty) {
      val otherTranspilers = Seq(
        new VueTranspiler(config, projectPath),
        new EjsTranspiler(config, projectPath),
        new PugTranspiler(config, projectPath)
      )
      val base = baseTranspilers.copy(transpilers =
        baseTranspilers.transpilers.prepended(new NuxtTranspiler(config, projectPath))
      )
      base +: otherTranspilers
    } else {
      Seq(baseTranspilers)
    }
  }

  private def extractNpmRcModules(npmrc: File): Seq[String] = {
    if (npmrc.exists) {
      val npmrcContent = FileUtils.readLinesInFile(npmrc.path)
      npmrcContent.collect {
        case line if line.contains(FileDefaults.REGISTRY_MARKER) =>
          line.substring(0, line.indexOf(FileDefaults.REGISTRY_MARKER))
      }
    } else {
      Seq.empty
    }
  }

  def handlePrivateModules(): List[(Path, Path)] = {
    val project           = File(config.srcDir)
    val nodeModulesFolder = project / NODE_MODULES_DIR_NAME
    if (!nodeModulesFolder.exists) {
      List.empty
    } else {
      val privateModulesToCopy = config.privateDeps ++ extractNpmRcModules(project / NPMRC_NAME)
      if (privateModulesToCopy.nonEmpty) {
        val slPrivateDir = File(projectPath) / PRIVATE_MODULES_DIR_NAME
        slPrivateDir.createDirectoryIfNotExists()

        val nodeModulesFolderContent =
          nodeModulesFolder.collectChildren(_.isDirectory, maxDepth = 1).toSet

        val foldersToCopy = privateModulesToCopy.collect {
          case module if nodeModulesFolderContent.exists(_.name.startsWith(module)) =>
            nodeModulesFolderContent.filter(f => f.name.startsWith(module))
          case module =>
            logger.debug(
              s"Could not find '$module' in '$nodeModulesFolder'. " +
                s"Ensure that npm authentication to your private registry is working " +
                s"to use private namespace analysis feature"
            )
            Set.empty
        }.flatten

        foldersToCopy.foreach { folder =>
          logger.debug(s"Copying private module '${folder.name}' to '$slPrivateDir'.")
          Try(
            folder.copyToDirectory(slPrivateDir)(
              linkOptions = LinkOptions.noFollow,
              copyOptions = Seq(StandardCopyOption.REPLACE_EXISTING) ++ LinkOptions.noFollow
            )
          ).tap(
            _.failed
              .foreach(
                logger
                  .debug(s"Unable to copy private module '${folder.name}' to '$slPrivateDir': ", _)
              )
          )
        }

        FileUtils
          .getFileTree(slPrivateDir.path, config, List(JS_SUFFIX, MJS_SUFFIX))
          .map(f => (f, slPrivateDir.path))
      } else List.empty
    }
  }

  def execute(): Unit = {
    if (transpilers.exists(_.shouldRun())) {
      if (!transpilers.headOption.exists(_.validEnvironment())) {
        logger.error("npm is not available in your environment. Please install npm and node.js.")
        System.exit(1)
      }
      transpilers.takeWhile(_.run(tmpTranspileDir))
    }
  }

}

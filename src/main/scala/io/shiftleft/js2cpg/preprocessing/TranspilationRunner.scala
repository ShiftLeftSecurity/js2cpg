package io.shiftleft.js2cpg.preprocessing

import better.files.File
import better.files.File.LinkOptions
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults._
import io.shiftleft.js2cpg.io.FileUtils
import org.slf4j.LoggerFactory

import java.nio.file.{Path, StandardCopyOption}
import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps

class TranspilationRunner(projectPath: Path,
                          tmpTranspileDir: Path,
                          config: Config,
                          subDir: Option[Path] = None) {

  private val logger = LoggerFactory.getLogger(getClass)

  private val transpilers: Seq[Transpiler] = Seq(
    new TranspilerGroup(
      config,
      projectPath,
      Seq(
        new NuxtTranspiler(config, projectPath),
        new TypescriptTranspiler(config, projectPath, subDir = subDir),
        new BabelTranspiler(config, projectPath, subDir = subDir)
      )
    ),
    new VueTranspiler(config, projectPath),
    new EjsTranspiler(config, projectPath),
    new PugTranspiler(config, projectPath),
  )

  private def handlePrivateModules(): List[(Path, Path)] = {
    val project           = File(config.srcDir)
    val nodeModulesFolder = project / NODE_MODULES_DIR_NAME
    if (!nodeModulesFolder.exists) {
      List.empty
    } else {
      val npmrc          = project / NPMRC_NAME
      val registryMarker = ":registry="
      val privateModulesToCopy = config.privateDeps ++ (if (npmrc.exists) {
                                                          npmrc.lines.collect {
                                                            case line
                                                                if line.contains(registryMarker) =>
                                                              line.substring(
                                                                0,
                                                                line.indexOf(registryMarker))
                                                          }.toSeq
                                                        } else {
                                                          Seq.empty
                                                        })

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
              copyOptions = Seq(StandardCopyOption.REPLACE_EXISTING) ++ LinkOptions.noFollow)).tap(
            _.failed
              .foreach(logger
                .debug(s"Unable to copy private module '${folder.name}' to '$slPrivateDir': ", _)))
        }

        FileUtils
          .getFileTree(slPrivateDir.path, config, JS_SUFFIX)
          .map(f => (f, slPrivateDir.path))
      } else List.empty
    }
  }

  private def collectJsFiles(jsFiles: List[(Path, Path)], dir: Path): List[(Path, Path)] = {
    val transpiledJsFiles = FileUtils
      .getFileTree(dir, config, JS_SUFFIX)
      .map(f => (f, dir))
    jsFiles.filterNot {
      case (f, rootDir) =>
        val filename = f.toString.replace(rootDir.toString, "")
        transpiledJsFiles.exists(_._1.toString.endsWith(filename))
    } ++ transpiledJsFiles
  }

  private def transpile(jsFiles: List[(Path, Path)]): List[(Path, Path)] = {
    transpilers.takeWhile(_.run(tmpTranspileDir))
    collectJsFiles(jsFiles, tmpTranspileDir) ++ NuxtTranspiler.collectJsFiles(projectPath, config)
  }

  def execute(): List[(Path, Path)] = {
    val jsFiles = FileUtils
      .getFileTree(projectPath, config, JS_SUFFIX)
      .map(f => (f, projectPath))
    config match {
      case c if c.ignorePrivateDeps && !transpilers.exists(_.shouldRun()) => jsFiles
      case c if c.ignorePrivateDeps                                       => transpile(jsFiles)
      case _                                                              => transpile(jsFiles) ++ handlePrivateModules()
    }
  }

}

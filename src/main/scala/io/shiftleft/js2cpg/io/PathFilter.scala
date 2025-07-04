package io.shiftleft.js2cpg.io

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.*
import org.slf4j.LoggerFactory

import java.nio.file.{InvalidPathException, Path, Paths}
import scala.util.{Failure, Try}

case class PathFilter(
  rootPath: Path,
  config: Config,
  filterIgnoredFiles: Boolean,
  extensions: List[String],
  withNodeModuleFolder: Boolean = false
) extends (Path => FilterResult) {

  private val logger = LoggerFactory.getLogger(PathFilter.getClass)

  private val projectDir: String = Paths.get(config.srcDir).toAbsolutePath.toString

  private def shouldBeIgnoredByUserConfig(filePath: Path, config: Config): Boolean =
    config.ignoredFiles.contains(filePath) || config.ignoredFilesRegex.matches(filePath.toString)

  private def acceptFromNodeModulesFolder(path: Path): Boolean =
    withNodeModuleFolder && (".*" + NODE_MODULES_DIR_NAME + ".*").r.matches(path.toString)

  private def filterDir(dir: Path): FilterResult = {
    val relDir = rootPath.relativize(dir)
    Paths.get(dir.toString.replace(rootPath.toString, projectDir)) match {
      case dirPath if dirPath.toString == projectDir => Accepted()
      case dirPath
          if IGNORED_FOLDERS_REGEX.exists(_.matches(File(dirPath).name)) &&
            !acceptFromNodeModulesFolder(dirPath) =>
        Rejected(relDir, "folder ignored by default")
      case dirPath if config.ignoredFiles.exists(i => dirPath.toString.startsWith(i.toString)) =>
        Rejected(relDir, "folder ignored by user configuration")
      case _ =>
        Accepted()
    }
  }

  /** We only accept a file if it is a regular file and has the appropriate extension.
    *
    * @param file
    *   the file to inspect
    * @return
    *   true iff file is a regular file and has the appropriate extension
    */
  private def acceptFile(file: File): Boolean =
    file.isRegularFile && !file.extension.contains(DTS_SUFFIX) &&
      (extensions.isEmpty || file.extension.exists(extensions.contains))

  private def filterFile(file: Path): FilterResult = {
    val relFile = rootPath.relativize(file)
    val ignores = if (config.ignoreTests) {
      IGNORED_FILES_REGEX ++ IGNORED_TESTS_REGEX
    } else {
      IGNORED_FILES_REGEX
    }
    val path = Try(Paths.get(file.toString.replace(rootPath.toString, projectDir))).recoverWith {
      case exception: InvalidPathException =>
        logger.debug(s"Can't handle file '$file' while filtering.", exception)
        Failure(exception)
    }.toOption
    val filterResult: FilterResult = path match {
      // default file ignores:
      case Some(filePath)
          if filterIgnoredFiles &&
            ignores.exists(_.matches(filePath.toString)) &&
            !acceptFromNodeModulesFolder(filePath) =>
        Rejected(relFile, "file ignored by default")
      // minified ignores:
      case Some(filePath)
          if config.ignoreMinified &&
            JsFileChecks.isMinifiedFile(file) &&
            !acceptFromNodeModulesFolder(filePath) =>
        Rejected(relFile, "minified file")
      // user ignores:
      case Some(filePath) if shouldBeIgnoredByUserConfig(filePath, config) =>
        Rejected(relFile, "by user configuration")
      case None =>
        Rejected(relFile, "invalid path")
      case _ => Accepted()
    }

    (filterResult, File(file)) match {
      case (a: Accepted, f) if acceptFile(f) => a
      case (r: Rejected, _)                  => r
      case _                                 => NotValid()
    }

  }

  override def apply(path: Path): FilterResult = File(path) match {
    case file if file.isDirectory => filterDir(path)
    case _                        => filterFile(path)
  }

}

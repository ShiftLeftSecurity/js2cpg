package io.shiftleft.js2cpg.io

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults._
import org.slf4j.LoggerFactory

import java.nio.file.{InvalidPathException, Path, Paths}
import scala.util.{Failure, Try}

case class PathFilter(rootPath: Path,
                      config: Config,
                      filterIgnoredFiles: Boolean,
                      extension: Option[String])
    extends (Path => FilterResult) {

  private val logger = LoggerFactory.getLogger(PathFilter.getClass)

  private val projectDir: String = Paths.get(config.srcDir).toAbsolutePath.toString

  private def shouldBeIgnoredByUserConfig(filePath: Path, config: Config): Boolean =
    config.ignoredFiles.contains(filePath) || config.ignoredFilesRegex.matches(filePath.toString)

  private def filterDir(dir: Path): FilterResult = {
    val relDir = rootPath.relativize(dir)
    Paths.get(dir.toString.replace(rootPath.toString, projectDir)) match {
      case dirPath if IGNORED_FOLDERS_REGEX.exists(_.matches(File(dirPath).name)) =>
        Rejected(relDir, "folder ignored by default")
      case dirPath if config.ignoredFiles.exists(i => dirPath.toString.startsWith(i.toString)) =>
        Rejected(relDir, "folder ignored by user configuration")
      case _ =>
        Accepted()
    }
  }

  /**
    * We only accept a file if its a regular file and has the appropriate extension.
    *
    * @param file the file to inspect
    * @return true iff file is a regular file and has the appropriate extension
    */
  private def acceptFile(file: File): Boolean = extension match {
    case Some(ext) =>
      file.isRegularFile && !file.extension.contains(DTS_SUFFIX) && file.toString.endsWith(ext)
    case None =>
      file.isRegularFile && !file.extension.contains(DTS_SUFFIX)
  }

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
      case Some(filePath) if filterIgnoredFiles && ignores.exists(_.matches(filePath.toString)) =>
        Rejected(relFile, "file ignored by default")
      // minified ignores:
      case Some(filePath)
          if config.ignoreMinified && MINIFIED_PATH_REGEX.matches(filePath.toString) =>
        Rejected(relFile, "minified file")
      // user ignores:
      case Some(filePath) if shouldBeIgnoredByUserConfig(filePath, config) =>
        Rejected(relFile, "by user configuration")
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

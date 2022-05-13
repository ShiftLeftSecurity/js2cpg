package io.shiftleft.js2cpg.io

import better.files.File

import java.nio.file.{Files, FileVisitResult, Path, SimpleFileVisitor}
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults._
import io.shiftleft.utils.IOUtils
import org.slf4j.LoggerFactory

import java.nio.file.attribute.BasicFileAttributes
import scala.collection.concurrent.TrieMap
import scala.collection.{mutable, SortedMap}

object FileUtils {

  private val logger = LoggerFactory.getLogger(FileUtils.getClass)

  // we only want to print excluded files and folders once (Path -> Reason as String)
  private val excludedPaths = TrieMap.empty[Path, String]

  def logAndClearExcludedPaths(): Unit = {
    excludedPaths.foreach { case (path, reason) =>
      logger.debug(s"Excluded '$path' ($reason).")
    }
    excludedPaths.clear()
  }

  /** Cleans the given path as String and removes unwanted elements that occur during transpilation on the Windows
    * platform and/or CI environments.
    */
  def cleanPath(sourceFileName: String): String = {
    val replacedDots = sourceFileName
      .
        // replace leading relative path elements
      replace("../", "")
      .
      // replace Nul characters (happens in some internationalized source maps)
      replace("\u0000", "")

    val replacedFile = if ("""file:///.:.*""".r.matches(replacedDots)) {
      replacedDots.replace("file:///", "")
    } else {
      replacedDots
    }
    if (replacedFile.matches(".*\\.vue\\?.*$")) {
      replacedFile.substring(0, replacedFile.lastIndexOf(".vue") + 4)
    } else {
      replacedFile
    }
  }

  def getFileTree(
    rootPath: Path,
    config: Config,
    extensions: List[String],
    filterIgnoredFiles: Boolean = true
  ): List[Path] = {
    val fileCollector = FileCollector(PathFilter(rootPath, config, filterIgnoredFiles, extensions))
    Files.walkFileTree(rootPath, fileCollector)
    excludedPaths.addAll(fileCollector.excludedPaths)
    fileCollector.files
  }

  private def copyTo(from: File, destination: File, config: Config)(implicit
    copyOptions: File.CopyOptions = File.CopyOptions(false)
  ): File = {
    val fileCollector = FileCollector(
      PathFilter(
        from.path,
        config,
        filterIgnoredFiles = false,
        extensions = List.empty,
        withNodeModuleFolder = config.withNodeModuleFolder
      )
    )
    if (from.isDirectory) {
      Files.walkFileTree(
        from.path,
        new SimpleFileVisitor[Path] {
          private def newPath(subPath: Path): Path =
            destination.path.resolve(from.path.relativize(subPath))

          override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
            fileCollector.preVisitDirectory(dir, attrs) match {
              case c @ FileVisitResult.CONTINUE =>
                Files.createDirectories(newPath(dir))
                c
              case other => other
            }

          override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
            val result = fileCollector.visitFile(file, attrs)
            if (!fileCollector.wasExcluded(file)) {
              Files.copy(file, newPath(file), copyOptions: _*)
            }
            result
          }
        }
      )
    } else {
      Files.copy(from.path, destination.path, copyOptions: _*)
    }
    excludedPaths.addAll(fileCollector.excludedPaths)
    destination
  }

  def copyToDirectory(from: File, directory: File, config: Config)(implicit
    copyOptions: File.CopyOptions = File.CopyOptions.default
  ): File = {
    copyTo(from, directory / from.name, config)(copyOptions)
  }

  def readLinesInFile(path: Path): Seq[String] =
    EmScriptenCleaner.clean(IOUtils.readLinesInFile(path))

  def contentMapFromFile(path: Path): Map[Int, String] =
    readLinesInFile(path).zipWithIndex.map { case (line, lineNumber) =>
      lineNumber -> line
    }.toMap

  def positionLookupTables(source: String): (SortedMap[Int, Int], SortedMap[Int, Int]) = {
    val positionToLineNumber, positionToFirstPositionInLine = mutable.TreeMap.empty[Int, Int]

    val data                = source.toCharArray
    var lineNumber          = 1
    var firstPositionInLine = 0
    var position            = 0

    while (position < data.length) {
      val isNewLine = data(position) == '\n'
      if (isNewLine) {
        positionToLineNumber.put(position, lineNumber)
        lineNumber += 1
        positionToFirstPositionInLine.put(position, firstPositionInLine)
        firstPositionInLine = position + 1
      }
      position += 1
    }

    positionToLineNumber.put(position, lineNumber)
    positionToFirstPositionInLine.put(position, firstPositionInLine)

    (positionToLineNumber, positionToFirstPositionInLine)
  }

  final case class FileStatistics(linesOfCode: Long, longestLineLength: Int, containsMarker: Boolean)

  /** Calculates various statistics of the source.
    *   - lines of code
    *   - longest line of code
    *   - containment of a given marker
    *
    * This implementation is just as fast as the unix word count program `wc -l`. By using Scala BufferedSource we gain
    * a lot of performance as it uses a Java PushbackReader and BufferedReader.
    */
  def fileStatistics(lines: Seq[String]): FileStatistics = {
    var linesOfCode       = 0L
    var longestLineLength = 0
    var containsMarker    = false

    for (line <- lines) {
      val currLength = line.length
      if (currLength > longestLineLength) {
        longestLineLength = currLength
      }
      if (!containsMarker && EMSCRIPTEN_START_FUNCS.matches(line)) {
        containsMarker = true
      }
      linesOfCode += 1
    }
    FileStatistics(linesOfCode, longestLineLength, containsMarker)
  }

}

package io.shiftleft.js2cpg.io

import io.shiftleft.js2cpg.core.Js2cpgArgumentsParser
import io.shiftleft.js2cpg.io.FileDefaults._
import io.shiftleft.js2cpg.io.FileUtils.FileStatistics
import io.shiftleft.utils.IOUtils
import org.slf4j.LoggerFactory

import java.nio.file.Path
import scala.collection.mutable

object JsFileChecks {

  private val logger = LoggerFactory.getLogger(JsFileChecks.getClass)

  private def printPerformanceHints(relPath: String, reasons: Seq[String]): Unit = {
    logger.debug(
      s"""The file '$relPath' may have negative impact on the analyzing performance!
         | ${if (reasons.length > 1) "Reasons:" else "Reason:"}
         | ${reasons.mkString(System.lineSeparator())}
         | Please check if:
         | \t- this file is the result of your build process
         | \t- this file is the result of applying transpilation tools (e.g., Typescript, Emscripten)
         | You might want to exclude this file when running js2cpg by adding it to '--${Js2cpgArgumentsParser.EXCLUDE}'.""".stripMargin
    )
  }

  def isMinifiedFile(path: String, fileStatistics: FileStatistics): Boolean = {
    MINIFIED_PATH_REGEX.matches(path) || fileStatistics.longestLineLength >= LINE_LENGTH_THRESHOLD
  }

  def isMinifiedFile(path: Path): Boolean = path.toString match {
    case p if MINIFIED_PATH_REGEX.matches(p) => true
    case p if p.endsWith(".js") =>
      val fileStatistics = FileUtils.fileStatistics(path)
      fileStatistics.longestLineLength >= LINE_LENGTH_THRESHOLD && fileStatistics.linesOfCode <= 50
    case _ => false
  }

  def check(relPath: String, lines: Seq[String]): FileStatistics = {
    val fileStatistics = FileUtils.fileStatistics(lines.iterator)
    val reasons        = mutable.ArrayBuffer.empty[String]

    // check for very large files (many lines):
    if (fileStatistics.linesOfCode > NUM_LINES_THRESHOLD) {
      reasons.append(s"\t- it contains more than $NUM_LINES_THRESHOLD lines of code")
    }

    // check for WebAssembly:
    if (fileStatistics.containsMarker) {
      reasons.append("\t- it contains WebAssembly code")
    }

    // check for being a minified js file
    if (isMinifiedFile(relPath, fileStatistics)) {
      reasons.append("\t- it appears to be a minified Javascript file")
    }

    if (reasons.nonEmpty) {
      printPerformanceHints(relPath, reasons.toSeq)
    }
    fileStatistics
  }

}

package io.shiftleft.js2cpg.io

import io.shiftleft.js2cpg.core.Js2cpgArgumentsParser
import io.shiftleft.js2cpg.io.FileDefaults._
import io.shiftleft.js2cpg.io.FileUtils.FileStatistics
import org.slf4j.LoggerFactory

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

  def check(relPath: String, lines: Seq[String]): FileStatistics = {
    val fileStatistics = FileUtils.fileStatistics(lines)
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
    if (MINIFIED_PATH_REGEX.matches(relPath)) {
      reasons.append("\t- it appears to be a minified Javascript file")
    }

    // check for being stored in a build/ or dist/ folder during build or distribution process
    if (BUILD_PATH_REGEX.matches(relPath)) {
      reasons.append(
        "\t- it is stored or copied to a www, dist, build or vendor folder during the build or distribution process"
      )
    }

    if (reasons.nonEmpty) {
      printPerformanceHints(relPath, reasons.toSeq)
    }
    fileStatistics
  }

}

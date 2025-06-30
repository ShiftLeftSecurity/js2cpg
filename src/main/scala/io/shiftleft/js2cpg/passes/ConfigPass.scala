package io.shiftleft.js2cpg.passes

import io.joern.x2cpg.utils.{Report, TimeUtils}
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.NewConfigFile
import io.shiftleft.js2cpg.io.FileDefaults
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.passes.ForkJoinParallelCpgPass
import io.shiftleft.utils.IOUtils
import org.slf4j.{Logger, LoggerFactory}

import java.nio.file.Path

class ConfigPass(filenames: List[(Path, Path)], cpg: Cpg, report: Report)
    extends ForkJoinParallelCpgPass[(Path, Path)](cpg) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  protected def fileContent(filePath: Path): Seq[String] = IOUtils.readLinesInFile(filePath)

  override def generateParts(): Array[(Path, Path)] = filenames.toArray

  override def runOnPart(diffGraph: DiffGraphBuilder, file: (Path, Path)): Unit = {
    val (filePath, fileRootPath) = file
    val relativeFile             = fileRootPath.relativize(filePath)
    val fileStatistics           = FileUtils.fileStatistics(filePath)
    if (fileStatistics.linesOfCode > FileDefaults.NUM_LINES_THRESHOLD) {
      logger.info(
        s"Skip adding file '$relativeFile' as config file (more than ${FileDefaults.NUM_LINES_THRESHOLD} lines)"
      )
    } else if (fileStatistics.longestLineLength > FileDefaults.LINE_LENGTH_THRESHOLD) {
      logger.info(
        s"Skip adding file '$relativeFile' as config file (at least one line longer than ${FileDefaults.LINE_LENGTH_THRESHOLD} characters)"
      )
    } else {
      val fileName = relativeFile.toString
      val content  = fileContent(filePath)
      val (result, time) = TimeUtils.time {
        val localDiff = Cpg.newDiffGraphBuilder
        logger.debug(s"Adding file '$relativeFile' as config file.")
        val configNode = NewConfigFile().name(fileName).content(content.mkString("\n"))
        report.addReportInfo(fileName, fileStatistics.linesOfCode, parsed = true)
        localDiff.addNode(configNode)
        localDiff
      }
      diffGraph.absorb(result)
      report.updateReport(fileName, true, time)
    }
  }

}

package io.shiftleft.js2cpg.cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.NewConfigFile
import io.shiftleft.js2cpg.core.Report
import io.shiftleft.js2cpg.io.{FileDefaults, TimeUtils}
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.passes.{ConcurrentWriterCpgPass, DiffGraph, IntervalKeyPool}
import org.slf4j.{Logger, LoggerFactory}

import java.nio.file.Path

class ConfigPass(filenames: List[(Path, Path)], cpg: Cpg, keyPool: IntervalKeyPool, report: Report)
    extends ConcurrentWriterCpgPass[(Path, Path)](cpg, keyPool = Some(keyPool)) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  protected def isConfigFile(fileName: String): Boolean =
    FileDefaults.CONFIG_FILES.exists(fileName.endsWith)

  protected def fileContent(filePath: Path): Iterable[String] =
    FileUtils.readLinesInFile(filePath)

  override def generateParts(): Array[(Path, Path)] = filenames.toArray

  override def runOnPart(diffGraph: DiffGraph.Builder, file: (Path, Path)): Unit = {
    val (filePath, fileRootPath) = file
    val relativeFile             = fileRootPath.relativize(filePath)
    val fileName                 = relativeFile.toString

    logger.debug(s"Adding file '$relativeFile' as config.")

    val (result, time) = TimeUtils.time {
      val localDiff  = DiffGraph.newBuilder
      val content    = fileContent(filePath)
      val loc        = content.size
      val configNode = NewConfigFile().name(fileName).content(content.mkString("\n"))

      report.addReportInfo(
        fileName,
        loc.toLong,
        parsed = true,
        cpgGen = true,
        isConfig = isConfigFile(fileName)
      )

      localDiff.addNode(configNode)
      localDiff
    }

    diffGraph.moveFrom(result)
    report.updateReportDuration(fileName, time)
  }

}

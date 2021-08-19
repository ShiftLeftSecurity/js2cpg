package io.shiftleft.js2cpg.cpg.passes

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.NewConfigFile
import io.shiftleft.js2cpg.core.Report
import io.shiftleft.js2cpg.io.{FileDefaults, TimeUtils}
import io.shiftleft.passes.{DiffGraph, IntervalKeyPool, ParallelCpgPass}
import org.slf4j.{Logger, LoggerFactory}

import java.nio.charset.{MalformedInputException, StandardCharsets}
import java.nio.file.Path

class ConfigPass(filenames: List[(Path, Path)], cpg: Cpg, keyPool: IntervalKeyPool, report: Report)
    extends ParallelCpgPass[(Path, Path)](cpg, keyPools = Some(keyPool.split(filenames.size))) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  protected def isConfigFile(fileName: String): Boolean =
    FileDefaults.CONFIG_FILES.exists(fileName.endsWith)

  protected def fileContent(filePath: Path): Iterable[String] =
    File(filePath).lines(StandardCharsets.UTF_8)

  override def partIterator: Iterator[(Path, Path)] = filenames.iterator

  override def runOnPart(file: (Path, Path)): Iterator[DiffGraph] = {
    val diffGraph = DiffGraph.newBuilder

    val filePath     = file._1
    val fileRootPath = file._2
    val relativeFile = fileRootPath.relativize(filePath)
    val fileName     = relativeFile.toString

    logger.debug(s"Adding file '$relativeFile' as config.")

    val (result, time) = TimeUtils.time {
      try {
        val content    = fileContent(filePath)
        val loc        = content.size
        val configNode = NewConfigFile().name(fileName).content(content.mkString("\n"))

        report.addReportInfo(fileName,
                             loc.toLong,
                             parsed = true,
                             cpgGen = true,
                             isConfig = isConfigFile(fileName))

        diffGraph.addNode(configNode)
      } catch {
        case ex: MalformedInputException =>
          logger.warn(s"failed to read file '$fileName' as UTF-8", ex)

          report.addReportInfo(fileName,
                               loc = 0,
                               parsed = false,
                               cpgGen = false,
                               isConfig = isConfigFile(fileName))
      }
      diffGraph.build()
    }
    report.updateReportDuration(fileName, time)
    Iterator(result)
  }

}

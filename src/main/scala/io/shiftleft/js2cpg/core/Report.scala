package io.shiftleft.js2cpg.core

import io.shiftleft.js2cpg.io.TimeUtils
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.Duration

object Report {

  private val logger = LoggerFactory.getLogger(Report.getClass)

  private type FileName = String

  private type Reports = TrieMap[FileName, ReportEntry]

  private case class ReportEntry(
    loc: Long,
    parsed: Boolean,
    cpgGen: Boolean,
    duration: Long,
    isConfig: Boolean = false
  ) {
    def toSeq: Seq[String] = {
      val lines = loc.toString
      val dur   = if (duration == 0) "-" else TimeUtils.pretty(Duration.fromNanos(duration))
      val es6   = if (parsed) "yes" else "no"
      val cpg   = if (cpgGen) "yes" else "no"
      Seq(lines, es6, cpg, dur)
    }
  }

}

class Report {

  import Report._

  private val reports: Reports = TrieMap.empty

  def print(): Unit = {

    def formatTable(table: Seq[Seq[String]]): String = {
      if (table.isEmpty) ""
      else {
        // Get column widths based on the maximum cell width in each column (+2 for a one character padding on each side)
        val colWidths =
          table.transpose.map(_.map(cell => if (cell == null) 0 else cell.length).max + 2)
        // Format each row
        val rows = table.map(
          _.zip(colWidths)
            .map { case (item, size) => (" %-" + (size - 1) + "s").format(item) }
            .mkString("|", "|", "|")
        )
        // Formatted separator row, used to separate the header and draw table borders
        val separator = colWidths.map("-" * _).mkString("+", "+", "+")
        // Put the table together and return
        val header  = rows.head
        val content = rows.tail.take(rows.tail.size - 1)
        val footer  = rows.tail.last
        (separator +: header +: separator +: content :+ separator :+ footer :+ separator)
          .mkString("\n")
      }
    }

    val rows = reports.toSeq
      .sortBy(_._1)
      .zipWithIndex
      .view
      .map {
        case ((file, sum), index) if sum.isConfig =>
          s"${index + 1}" +: s"$file (config file)" +: sum.toSeq
        case ((file, sum), index) => s"${index + 1}" +: file +: sum.toSeq
      }
      .toSeq
    val numOfReports = reports.size
    val header       = Seq(Seq("#", "File", "LOC", "Parsed", "Got a CPG", "CPG Gen. Duration"))
    val footer = Seq(
      Seq(
        "Total",
        "",
        s"${reports.map(_._2.loc).sum}",
        s"${reports.count(_._2.parsed)}/$numOfReports",
        s"${reports.count(_._2.cpgGen)}/$numOfReports",
        ""
      )
    )
    val table = header ++ rows ++ footer
    logger.info(s"Report:${System.lineSeparator()}" + formatTable(table))
  }

  def addReportInfo(
    fileName: FileName,
    loc: Long,
    parsed: Boolean = false,
    cpgGen: Boolean = false,
    duration: Long = 0,
    isConfig: Boolean = false
  ): Unit =
    reports(fileName) = ReportEntry(loc, parsed, cpgGen, duration, isConfig)

  def updateReportDuration(fileName: FileName, duration: Long): Unit =
    reports.updateWith(fileName)(_.map(_.copy(cpgGen = true, duration = duration)))

}

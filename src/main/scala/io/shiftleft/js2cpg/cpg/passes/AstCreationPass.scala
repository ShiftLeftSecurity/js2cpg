package io.shiftleft.js2cpg.cpg.passes

import java.nio.file.Path
import better.files.File
import com.oracle.js.parser.Source
import com.oracle.js.parser.ir.FunctionNode
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.Report
import io.shiftleft.js2cpg.cpg.passes.astcreation.AstCreator
import io.shiftleft.js2cpg.io.{FileUtils, JsFileChecks, TimeUtils}
import io.shiftleft.js2cpg.parser.{JavaScriptParser, JsSource}
import io.shiftleft.passes.{DiffGraph, IntervalKeyPool, ConcurrentWriterCpgPass}
import org.slf4j.LoggerFactory
import io.shiftleft.js2cpg.util.SourceWrapper._

import scala.util.{Failure, Success, Try}

/**
  * Given a list of filenames, this pass creates the abstract syntax tree and CPG AST for each file.
  * Files are processed in parallel.
  */
class AstCreationPass(srcDir: File,
                      filenames: List[(Path, Path)],
                      cpg: Cpg,
                      keyPool: IntervalKeyPool,
                      report: Report)
    extends ConcurrentWriterCpgPass[(Path, Path)](cpg, keyPool = Some(keyPool)) {

  private val logger = LoggerFactory.getLogger(getClass)

  private case class ParseResult(file: File, jsSource: JsSource, ast: FunctionNode)

  override def generateParts(): Array[(Path, Path)] = filenames.toArray

  override def runOnPart(diffGraph: DiffGraph.Builder, filename: (Path, Path)): Unit = {
    val localDiff = DiffGraph.newBuilder
    val file      = filename._1
    val fileRoot  = filename._2

    val parseResult = parse(file, fileRoot) match {
      case Failure(parseException) =>
        logger.warn(parseException.getMessage)
        None
      case Success(parseResult) =>
        Some((parseResult, preAnalyze(parseResult)))
    }

    parseResult.map {
      case (parseResult, usedIdentNodes) =>
        val (result, duration) = {
          TimeUtils.time(generateCpg(parseResult, localDiff, usedIdentNodes))
        }
        val path = parseResult.jsSource.originalFilePath
        result match {
          case Failure(exception) =>
            logger.warn(s"Failed to generate CPG for '$path'!", exception)
          case _ =>
            logger.info(s"Processed file '$path'")
            report.updateReportDuration(path, duration)
            diffGraph.moveFrom(localDiff)
        }
    }
  }

  private def generateCpg(parseResult: ParseResult,
                          diffGraph: DiffGraph.Builder,
                          usedIdentNodes: Set[String]): Try[Unit] = {
    Try {
      val source = parseResult.jsSource
      val ast    = parseResult.ast
      logger.debug(s"Generating CPG for file '${source.originalFilePath}'.")
      val astBuilderPass = new AstCreator(diffGraph, source, usedIdentNodes)
      astBuilderPass.convert(ast)
    }
  }

  private def preAnalyze(parseResult: ParseResult): Set[String] = {
    val ast                = parseResult.ast
    val usedIdentNodesPass = new UsedIdentNodesPass()
    ast.accept(usedIdentNodesPass)
    usedIdentNodesPass.usedIdentNodes.toSet
  }

  private def parse(path: Path, rootDir: Path): Try[ParseResult] = {
    val lines   = FileUtils.readLinesInFile(path)
    val relPath = rootDir.relativize(path).toString

    val fileStatistics = JsFileChecks.check(relPath, lines)

    val source   = Source.sourceFor(relPath, lines.mkString("\n"))
    val jsSource = source.toJsSource(srcDir, rootDir)

    logger.debug(s"Parsing file '$relPath'.")
    Try(JavaScriptParser.parseFromSource(jsSource)) match {
      case Failure(exception) =>
        report.addReportInfo(jsSource.originalFilePath, fileStatistics.linesOfCode)
        Failure(exception)
      case Success((ast, jsSource)) =>
        report.addReportInfo(jsSource.originalFilePath, fileStatistics.linesOfCode, parsed = true)
        Success(ParseResult(File(path), jsSource, ast))
    }
  }

}

package io.shiftleft.js2cpg.passes

import java.nio.file.Path
import better.files.File
import com.oracle.js.parser.Source
import com.oracle.js.parser.ir.FunctionNode
import io.joern.x2cpg.utils.{Report, TimeUtils}
import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.js2cpg.astcreation.AstCreator
import io.shiftleft.js2cpg.io.{FileUtils, JsFileChecks}
import io.shiftleft.js2cpg.parser.{JavaScriptParser, JsSource}
import io.shiftleft.passes.ForkJoinParallelCpgPass
import org.slf4j.LoggerFactory
import io.shiftleft.js2cpg.utils.SourceWrapper.*

import scala.util.{Failure, Success, Try}

/** Given a list of filenames, this pass creates the abstract syntax tree and CPG AST for each file. Files are processed
  * in parallel.
  */
class AstCreationPass(srcDir: File, filenames: List[(Path, Path)], cpg: Cpg, report: Report)
    extends ForkJoinParallelCpgPass[(Path, Path)](cpg) {

  private val logger = LoggerFactory.getLogger(getClass)

  private case class ParseResult(file: File, jsSource: JsSource, ast: FunctionNode)

  override def generateParts(): Array[(Path, Path)] = filenames.toArray

  override def runOnPart(diffGraph: DiffGraphBuilder, input: (Path, Path)): Unit = {
    val (file, fileRoot) = input
    parse(file, fileRoot) match {
      case Success(parseResult) =>
        val usedIdentNodes = collectUsedIdentNodes(parseResult)
        val path           = parseResult.jsSource.originalFilePath
        val (result, duration) = TimeUtils.time {
          generateCpg(parseResult, Cpg.newDiffGraphBuilder, usedIdentNodes)
        }
        result match {
          case Success(localDiff) =>
            logger.info(s"Processed file '$path'")
            report.updateReport(path, true, duration)
            diffGraph.absorb(localDiff)
          case Failure(exception) =>
            logger.warn(s"Failed to generate CPG for '$path'!", exception)
        }
      case Failure(parseException) =>
        logger.warn(parseException.getMessage)
    }
  }

  private def generateCpg(
    parseResult: ParseResult,
    diffGraph: DiffGraphBuilder,
    usedIdentNodes: Set[String]
  ): Try[DiffGraphBuilder] = {
    Try {
      val source = parseResult.jsSource
      val ast    = parseResult.ast
      logger.debug(s"Generating CPG for file '${source.originalFilePath}'.")
      val astBuilderPass = new AstCreator(diffGraph, source, usedIdentNodes)
      astBuilderPass.convert(ast)
      diffGraph
    }
  }

  private def collectUsedIdentNodes(parseResult: ParseResult): Set[String] = {
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

package io.shiftleft.js2cpg.parser

import java.nio.file.{Path, Paths}
import better.files.File
import com.atlassian.sourcemap.{ReadableSourceMap, ReadableSourceMapImpl}
import com.oracle.js.parser.Source
import com.oracle.js.parser.ir.Node
import io.shiftleft.js2cpg.io.FileDefaults._
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.js2cpg.preprocessing.NuxtTranspiler
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class JsSource(val srcDir: File, val projectDir: Path, val source: Source) {

  private val logger = LoggerFactory.getLogger(getClass)

  private val absoluteFilePath = (File(projectDir.toAbsolutePath) / originalFilePath).pathAsString
  private val mapFilePath      = absoluteFilePath + ".map"
  private val sourceMap        = sourceMapOrigin()

  private val (positionToLineNumberMapping, positionToFirstPositionInLineMapping) =
    FileUtils.positionLookupTables(source.getString)

  // maximum length of re-mapped code fields after transpilation in number of characters
  private val MAX_CODE_LENGTH = 100

  private case class SourceMapOrigin(sourceFilePath: Path,
                                     sourceMap: Option[ReadableSourceMap],
                                     sourceWithLineNumbers: Map[Int, String])

  /**
    * @return the file path of the parsed file.
    *         If this file is the result of transpilation the original source file path
    *         is calculated from the corresponding sourcemap.
    */
  def filePath: String = filePathFromSourceMap

  /**
    * @return the line number of a node in the parsed file.
    *         If this file is the result of transpilation the original line number
    *         is calculated from the corresponding sourcemap.
    */
  def getLine(node: Node): Option[Int] = lineFromSourceMap(node)

  /**
    * @return the column number of a node in the parsed file.
    *         If this file is the result of transpilation the original column number
    *         is calculated from the corresponding sourcemap.
    */
  def getColumn(node: Node): Option[Int] = columnFromSourceMap(node)

  /**
    * @return the code of a node in the parsed file.
    *         If this file is the result of transpilation the original code
    *         is calculated from the corresponding sourcemap.
    *         Note: in this case, only the re-mapped starting line/column number are available. Hence,
    *         we extract only a fixed number of characters (max. until the end of the file).
    */
  def getCode(node: Node): String = codeFromSourceMap(node)

  def getString(node: Node): String = source.getString(node.getToken)

  /**
    * @return always the original file that was parsed. Might be a file that is the result of transpilation
    */
  def originalFilePath: String = {
    Option(source.getURL) match {
      case Some(url) => url.getPath
      case None      => source.getName
    }
  }

  private def constructSourceFilePath(sourceFileName: String): File = sourceFileName match {
    case _
        if absoluteFilePath
          .contains(NuxtTranspiler.NUXT_FOLDER) && srcDir.path.compareTo(projectDir) == 0 =>
      // For nuxt-js transpilation we have the same src and project dir and we need some special handling here
      File(absoluteFilePath).parent / sourceFileName
    case _ if sourceFileName.startsWith(WEBPACK_PREFIX) =>
      // Additionally, source map files coming from webpack (e.g., from Vue transpilation) are somewhat hidden
      val replacedName = sourceFileName.replace(WEBPACK_PREFIX, "")
      srcDir / replacedName
    case _ =>
      val cleanedPath = FileUtils.cleanPath(sourceFileName)
      // having "/" here is fine as JS source maps always have platform independent path separators
      val lookupPath = if (cleanedPath.contains("/" + srcDir.name + "/")) {
        cleanedPath.substring(
          cleanedPath.lastIndexOf("/" + srcDir.name + "/") + srcDir.name.length + 2)
      } else {
        cleanedPath
      }
      val srcFilePath: File = if (cleanedPath.contains("AppData/Local/Temp")) {
        // special handling for Windows CI
        srcDir.root / "Users" / cleanedPath.replace(srcDir.toString(), "")
      } else {
        val lookupFile = File(lookupPath)
        if (lookupFile.parent != lookupFile.root) {
          srcDir / lookupPath
        } else {
          srcDir / lookupFile.name
        }
      }
      srcFilePath
  }

  private def sourceMapOrigin(): Option[SourceMapOrigin] = {
    if (File(mapFilePath).isEmpty) {
      logger.debug(s"No source map file available for '$originalFilePath'")
      None
    } else {
      val sourceMapContent = FileUtils.readLinesInFile(Paths.get(mapFilePath)).mkString("\n")
      // We apply a Try here as some source maps are indeed un-parsable by ReadableSourceMap:
      Try(ReadableSourceMapImpl.fromSource(sourceMapContent)) match {
        case Failure(exception) =>
          logger.debug(s"Invalid source map file for '$originalFilePath'", exception)
          None
        case Success(sourceMap) =>
          val sourceFileNames = sourceMap.getSources.asScala.filter(_ != null)
          // The source file might not exist, e.g., if it was the result of transpilation
          // but is not delivered and still referenced in the source map
          // (fix for: https://github.com/ShiftLeftSecurity/product/issues/4994)
          val sourceFile = sourceFileNames
            .find(_.toLowerCase.endsWith(File(absoluteFilePath).nameWithoutExtension + VUE_SUFFIX))
            .orElse(sourceFileNames.headOption)

          sourceFile.flatMap { sourceFileName =>
            val sourceFilePath = constructSourceFilePath(sourceFileName)
            if (!sourceFilePath.exists) {
              logger.debug(
                s"Could not load source map file for '$originalFilePath'. The source map file refers to '$sourceFilePath' but this does not exist")
              None
            } else {
              val sourceFileMapping = FileUtils.contentMapFromFile(sourceFilePath.path)
              logger.debug(
                s"Successfully loaded source map file '$mapFilePath':" +
                  s"\n\t* Transpiled file: '$absoluteFilePath'" +
                  s"\n\t* Origin: '$sourceFilePath'")
              Some(SourceMapOrigin(sourceFilePath.path, Some(sourceMap), sourceFileMapping))
            }
          }
      }
    }
  }

  private def codeFromSourceMap(node: Node): String = {
    sourceMap match {
      case Some(SourceMapOrigin(_, Some(sourceMap), sourceWithLineNumbers)) =>
        val line   = getLineOfSource(node.getStart) - 1
        val column = getColumnOfSource(node.getStart)
        sourceMap.getMapping(line, column) match {
          case null =>
            source.getString(node.getStart, node.getFinish - node.getStart)
          case mapping =>
            val originLine   = mapping.getSourceLine
            val originColumn = mapping.getSourceColumn
            val transpiledCodeLength = node.getFinish - node.getStart match {
              // for some transpiled nodes the start and finish indices are wrong:
              case 0     => node.toString.length
              case other => other
            }
            sourceWithLineNumbers.get(originLine) match {
              case Some(startingCodeLine) =>
                // Code from the origin source file was found.
                val maxCodeLength = math.min(transpiledCodeLength, MAX_CODE_LENGTH)
                // We are extra careful: we do not want to generate empty lines.
                // That can happen e.g., for synthetic return statements.
                // Hence, we back up 1 char.
                val startingCode =
                  startingCodeLine.substring(
                    math.min(math.max(startingCodeLine.length - 1, 0), originColumn))
                calculateCode(
                  sourceWithLineNumbers,
                  startingCode,
                  originLine,
                  maxCodeLength
                )
              case None =>
                // It has an actual mapping, but it is synthetic code not found in the source file.
                // We return the synthetic code.
                source.getString(node.getStart, node.getFinish - node.getStart)
            }
        }
      case _ =>
        // No mapping at all. We return the node code.
        source.getString(node.getStart, node.getFinish - node.getStart)
    }
  }

  /**
    * Code field calculation:
    *  - We start with the re-mapped line/column number.
    *  - We always read at the length of the transpiled node (except if the original file ends earlier) capped at MAX_CODE_LENGTH.
    *  - If there would be more content we append ' [...]'.
    */
  @scala.annotation.tailrec
  private def calculateCode(sourceWithLineNumbers: Map[Int, String],
                            currentLine: String,
                            currentLineNumber: Int,
                            transpiledCodeLength: Int): String =
    currentLine match {
      case line if line.length >= transpiledCodeLength =>
        line.substring(0, transpiledCodeLength - 1).stripLineEnd + " [...]"
      case line
          if line.length < transpiledCodeLength && sourceWithLineNumbers.contains(
            currentLineNumber + 1) =>
        calculateCode(
          sourceWithLineNumbers,
          line + System.lineSeparator() + sourceWithLineNumbers(currentLineNumber + 1),
          currentLineNumber + 1,
          transpiledCodeLength
        )
      case line =>
        line.stripLineEnd
    }

  private def lineFromSourceMap(node: Node): Option[Int] = {
    sourceMap match {
      case Some(SourceMapOrigin(_, Some(sourceMap), _)) =>
        val line   = getLineOfSource(node.getStart) - 1
        val column = getColumnOfSource(node.getStart)
        Option(sourceMap.getMapping(line, column)).map(_.getSourceLine + 1)
      case _ =>
        Some(getLineOfSource(node.getStart))
    }
  }

  private def columnFromSourceMap(node: Node): Option[Int] = {
    sourceMap match {
      case Some(SourceMapOrigin(_, Some(sourceMap), _)) =>
        val line   = getLineOfSource(node.getStart) - 1
        val column = getColumnOfSource(node.getStart)
        Option(sourceMap.getMapping(line, column)).map(_.getSourceColumn)
      case _ =>
        Some(getColumnOfSource(node.getStart))
    }
  }

  private def filePathFromSourceMap: String = {
    sourceMap match {
      case Some(SourceMapOrigin(sourceFilePath, _, _))
          if absoluteFilePath.contains(NuxtTranspiler.NUXT_FOLDER) =>
        srcDir.relativize(File(NuxtTranspiler.remapPath(sourceFilePath.toString))).toString
      case Some(SourceMapOrigin(sourceFilePath, _, _)) =>
        srcDir.relativize(File(sourceFilePath)).toString
      case None if absoluteFilePath.contains(NuxtTranspiler.NUXT_FOLDER) =>
        NuxtTranspiler.remapPath(originalFilePath)
      case None =>
        originalFilePath
    }
  }

  // Returns the line number for a given position in the source.
  // We use this method instead of source.getLine for performance reasons.
  private def getLineOfSource(position: Int): Int = {
    val (_, lineNumber) = positionToLineNumberMapping.minAfter(position).get
    lineNumber
  }

  // Returns the column number for a given position in the source.
  // We use this method instead of source.getColumn for performance reasons.
  private def getColumnOfSource(position: Int): Int = {
    val (_, firstPositionInLine) = positionToFirstPositionInLineMapping.minAfter(position).get
    position - firstPositionInLine
  }

}

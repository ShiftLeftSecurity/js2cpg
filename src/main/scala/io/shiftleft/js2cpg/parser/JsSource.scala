package io.shiftleft.js2cpg.parser

import java.nio.file.{Path, Paths}
import better.files.File
import com.atlassian.sourcemap.{ReadableSourceMap, ReadableSourceMapImpl}
import com.oracle.js.parser.Source
import com.oracle.js.parser.ir.Node
import io.shiftleft.js2cpg.io.FileDefaults.*
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.js2cpg.preprocessing.NuxtTranspiler
import io.shiftleft.utils.IOUtils

import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

object JsSource {

  private val logger = LoggerFactory.getLogger(getClass)

  case class SourceMapOrigin(
    sourceFilePath: Path,
    sourceMap: Option[ReadableSourceMap],
    sourceWithLineNumbers: Map[Int, String]
  )

}

case class JsSource(srcDir: File, projectDir: Path, source: Source) {

  import JsSource._

  private val absoluteFilePath = (File(projectDir.toAbsolutePath) / originalFilePath).pathAsString
  private val mapFilePath      = absoluteFilePath + ".map"
  private val sourceMap        = sourceMapOrigin()

  def getSourceMap: Option[SourceMapOrigin] = sourceMap

  private val (positionToLineNumberMapping, positionToFirstPositionInLineMapping) =
    FileUtils.positionLookupTables(source.getContent)

  /** @return
    *   the file path of the parsed file. If this file is the result of transpilation the original source file path is
    *   calculated from the corresponding sourcemap.
    */
  def filePath: String = filePathFromSourceMap

  def getString(node: Node): String = source.getString(node.getToken)

  /** @return
    *   always the original file that was parsed. Might be a file that is the result of transpilation
    */
  def originalFilePath: String = {
    Option(source.getURL) match {
      case Some(url) => url.getPath
      case None      => source.getName
    }
  }

  private def constructSourceFilePath(sourceFileName: String): File = {
    if (sourceFileName.isEmpty) {
      srcDir / source.getName
    } else if (absoluteFilePath.contains(NuxtTranspiler.NUXT_FOLDER) && srcDir.path.compareTo(projectDir) == 0) {
      // For nuxt-js transpilation we have the same src and project dir and we need some special handling here
      if (sourceFileName.startsWith(WEBPACK_PREFIX)) {
        val replacedName = FileUtils.cleanPath(sourceFileName.replace(WEBPACK_PREFIX, ""))
        srcDir / replacedName.substring(replacedName.indexOf("/") + 1)
      } else {
        File(absoluteFilePath).parent / sourceFileName
      }
    } else if (sourceFileName.startsWith(WEBPACK_PREFIX)) {
      // Additionally, source map files coming from webpack (e.g., from Vue transpilation) are somewhat hidden
      val replacedName = sourceFileName.replace(WEBPACK_PREFIX, "")
      srcDir / replacedName.substring(replacedName.indexOf("/") + 1)
    } else {
      val cleanedPath = FileUtils.cleanPath(sourceFileName)
      // having "/" here is fine as JS source maps always have platform independent path separators
      val lookupPath = if (cleanedPath.contains("/" + srcDir.name + "/")) {
        cleanedPath.substring(cleanedPath.indexOf("/" + srcDir.name + "/") + srcDir.name.length + 2)
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
  }

  private def sourceMapOrigin(): Option[SourceMapOrigin] = {
    if (File(mapFilePath).isEmpty) {
      logger.debug(s"No source map file available for '$originalFilePath'")
      None
    } else {
      val sourceMapContent = IOUtils.readLinesInFile(Paths.get(mapFilePath)).mkString("\n")
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
            .find { f =>
              val fAsVue = File(absoluteFilePath).nameWithoutExtension + VUE_SUFFIX
              val fAsJs  = File(absoluteFilePath).nameWithoutExtension + JS_SUFFIX
              f.toLowerCase.endsWith(fAsVue) || f.endsWith(s"/$fAsJs")
            }
            .orElse(sourceFileNames.headOption)

          sourceFile.flatMap { sourceFileName =>
            val sourceFilePath = constructSourceFilePath(sourceFileName)
            if (!sourceFilePath.exists) {
              logger.debug(
                s"Could not load source map file for '$originalFilePath'. The source map file refers to '$sourceFilePath' but this does not exist"
              )
              None
            } else {
              val sourceFileMapping = FileUtils.contentMapFromFile(sourceFilePath.path)
              logger.debug(
                s"Successfully loaded source map file '$mapFilePath':" +
                  s"\n\t* Transpiled file: '$absoluteFilePath'" +
                  s"\n\t* Origin: '$sourceFilePath'"
              )
              Some(SourceMapOrigin(sourceFilePath.path, Some(sourceMap), sourceFileMapping))
            }
          }
      }
    }
  }

  def lineFromSourceMap(node: Node): Option[Int] = {
    sourceMap match {
      case Some(SourceMapOrigin(_, Some(sourceMap), _)) =>
        val line   = getLineOfSource(node.getStart) - 1
        val column = getColumnOfSource(node.getStart)
        Option(sourceMap.getMapping(line, column)).map(_.getSourceLine + 1)
      case _ =>
        Some(getLineOfSource(node.getStart))
    }
  }

  def columnFromSourceMap(node: Node): Option[Int] = {
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
      case Some(SourceMapOrigin(sourceFilePath, _, _)) if absoluteFilePath.contains(NuxtTranspiler.NUXT_FOLDER) =>
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
  def getLineOfSource(position: Int): Int = {
    val (_, lineNumber) = positionToLineNumberMapping.minAfter(position).get
    lineNumber
  }

  // Returns the column number for a given position in the source.
  // We use this method instead of source.getColumn for performance reasons.
  def getColumnOfSource(position: Int): Int = {
    val (_, firstPositionInLine) = positionToFirstPositionInLineMapping.minAfter(position).get
    position - firstPositionInLine
  }

}

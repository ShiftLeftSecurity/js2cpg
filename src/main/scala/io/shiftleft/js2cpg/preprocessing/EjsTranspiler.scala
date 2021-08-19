package io.shiftleft.js2cpg.preprocessing

import better.files.File
import com.atlassian.sourcemap.{SourceMap, SourceMapImpl}
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.{EJS_SUFFIX, JS_SUFFIX}
import io.shiftleft.js2cpg.io.FileUtils
import org.slf4j.LoggerFactory

import java.nio.file.Path
import scala.collection.{SortedMap, mutable}
import scala.util.{Failure, Success, Using}

class EjsTranspiler(override val config: Config, override val projectPath: Path)
    extends Transpiler {

  private val logger = LoggerFactory.getLogger(getClass)

  private val TAGS_REGEX = """(<%(?!%)[\s\S]*?[^%]%>)""".r

  private val TAG_GROUPS_REGEX = """^(<%[=\-_#]?)([\s\S]*?)([-_]?%>)$""".r

  private lazy val ejsFiles: List[Path] = allEjsFiles()

  private def allEjsFiles(): List[Path] =
    FileUtils.getFileTree(projectPath, config, EJS_SUFFIX)

  private def offset(str: String): Int =
    (str.trim.linesIterator.length - str.linesIterator.length) + 1

  private def extractJsCode(tpl: String,
                            positionToLineNumberMapping: SortedMap[Int, Int],
                            positionToFirstPositionInLineMapping: SortedMap[Int, Int],
                            ejsFileName: String): (String, SourceMap) = {
    val sourceMap = new SourceMapImpl()
    val result    = mutable.ArrayBuffer.empty[String]
    TAGS_REGEX.findAllIn(tpl).matchData.foreach { ma =>
      val tag   = ma.toString
      val start = ma.start
      val parse = TAG_GROUPS_REGEX.findAllIn(tag).subgroups

      val generatedLineNumber   = result.length
      var generatedColumnNumber = 0
      var sourceLineNumber      = 0
      var sourceColumnNumber    = 0

      val extractedJsCode = parse.head match {
        case t if t == "<%=" =>
          sourceLineNumber = getLineOfSource(positionToLineNumberMapping, start + 3)
          sourceColumnNumber = getColumnOfSource(positionToFirstPositionInLineMapping, start + 3)
          generatedColumnNumber = 2
          ";__append(escapeFn(" + parse(1).trim + "));"
        case t if t == "<%-" =>
          sourceLineNumber = getLineOfSource(positionToLineNumberMapping, start + 3)
          sourceColumnNumber = getColumnOfSource(positionToFirstPositionInLineMapping, start + 3)
          generatedColumnNumber = 2
          ";__append(" + parse(1).trim + ");"
        case t if t == "<%#" =>
          ""
        case t if t == "<%_" =>
          sourceLineNumber = getLineOfSource(positionToLineNumberMapping, start + 3)
          sourceColumnNumber = getColumnOfSource(positionToFirstPositionInLineMapping, start + 3)
          generatedColumnNumber = 0
          parse(1).trim
        case t if t == "<%" =>
          sourceLineNumber = getLineOfSource(positionToLineNumberMapping, start + 2)
          sourceColumnNumber = getColumnOfSource(positionToFirstPositionInLineMapping, start + 2)
          generatedColumnNumber = 0
          parse(1).trim
        case n =>
          throw new UnsupportedOperationException("Unknown EJS tag: " + n)
      }
      if (extractedJsCode.nonEmpty) {
        sourceMap.addMapping(generatedLineNumber,
                             generatedColumnNumber,
                             sourceLineNumber - offset(parse(1)),
                             sourceColumnNumber,
                             ejsFileName)
        result.append(extractedJsCode)
      }
    }
    (result.mkString("\n"), sourceMap)
  }

  override def shouldRun(): Boolean = config.templateTranspiling && ejsFiles.nonEmpty

  private def getLineOfSource(positionToLineNumberMapping: SortedMap[Int, Int],
                              position: Int): Int = {
    val (_, lineNumber) = positionToLineNumberMapping.minAfter(position).get
    lineNumber
  }

  private def getColumnOfSource(positionToFirstPositionInLineMapping: SortedMap[Int, Int],
                                position: Int): Int = {
    val (_, firstPositionInLine) = positionToFirstPositionInLineMapping.minAfter(position).get
    position - firstPositionInLine
  }

  private def transpileEjsFile(ejsFile: Path, tmpTranspileDir: Path): Unit = {
    val ejsFileName        = projectPath.relativize(ejsFile).toString
    val transpiledFileName = ejsFileName.stripSuffix(EJS_SUFFIX) + JS_SUFFIX
    val transpiledFile     = File(tmpTranspileDir) / transpiledFileName
    val sourceMapFile      = File(tmpTranspileDir) / (transpiledFileName + ".map")
    Using(FileUtils.bufferedSourceFromFile(ejsFile)) { ejsFileBuffer =>
      val ejsFileContent = FileUtils.contentFromBufferedSource(ejsFileBuffer)
      val (positionToLineNumberMapping, positionToFirstPositionInLineMapping) =
        FileUtils.positionLookupTables(ejsFileContent)
      val (jsCode, sourceMap) = extractJsCode(ejsFileContent,
                                              positionToLineNumberMapping,
                                              positionToFirstPositionInLineMapping,
                                              ejsFile.toString)
      transpiledFile.parent.createDirectoryIfNotExists(createParents = true)
      sourceMapFile.parent.createDirectoryIfNotExists(createParents = true)
      transpiledFile.writeText(jsCode)
      sourceMapFile.writeText(sourceMap.generate())
    } match {
      case Failure(exception) =>
        logger.debug(s"\t- could not transpile EJS template '$ejsFileName'. $exception")
      case Success(_) =>
        logger.debug(s"\t+ transpiled EJS template '$ejsFileName' to '$transpiledFile'")
    }
  }

  override protected def transpile(tmpTranspileDir: Path): Boolean = {
    ejsFiles.foreach(transpileEjsFile(_, tmpTranspileDir))
    logger.debug(s"\t+ EJS template transpiling finished.")
    true
  }

  override def validEnvironment(): Boolean = true

  override protected def logExecution(): Unit =
    logger.info(s"EJS - transpiling source files in '${File(projectPath).name}'")

}

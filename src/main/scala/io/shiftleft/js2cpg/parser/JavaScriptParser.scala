package io.shiftleft.js2cpg.parser

import com.oracle.js.parser.{ErrorManager, Parser, ParserException, ScriptEnvironment, Source}
import com.oracle.js.parser.ir.{ErrorNode, FunctionNode}
import io.shiftleft.js2cpg.util.SourceWrapper._
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object JavaScriptParser {

  private val logger = LoggerFactory.getLogger(JavaScriptParser.getClass)

  // 11 is internally used by graaljs for the ECMA script version 2020.
  // Sadly the do not provide a define for this.
  private val ecmaVersion2020 = 11

  private val moduleName = ":program"

  private val nodeJsFixWrapper: Seq[String] => Seq[String] = {
    case l if l.isEmpty => l
    case l if l.length == 1 =>
      Seq(s"(function (exports, require, module, __filename, __dirname) { ${l.head} });")
    case l if l.length == 2 =>
      Seq(s"(function (exports, require, module, __filename, __dirname) { ${l.head}",
          s"${l(1)} });")
    case l =>
      val (head, body, last) = (l.head, l.tail.slice(0, l.tail.length - 1), l.last)
      s"(function (exports, require, module, __filename, __dirname) { $head" +: body :+ s"$last });"
  }

  private val importRegex = """(import.*from\s*(".*"|'.*')|import\s*(".*"|'.*'));?""".r

  private class Js2CpgErrMgr(parsingMode: String) extends ErrorManager {
    private val infoMessages: ListBuffer[String]  = mutable.ListBuffer.empty[String]
    private val errorMessages: ListBuffer[String] = mutable.ListBuffer.empty[String]

    override def message(message: String): Unit = {
      infoMessages.addOne(message)
    }
    override def error(message: String): Unit = {
      errorMessages.addOne(message)
    }

    def containsNodeJSInvalidReturn: Boolean =
      errorMessages.exists(_.contains("Invalid return statement"))

    def printMessages(): Unit = {
      infoMessages.foreach(message => logger.debug(s"Parsing error in $parsingMode: $message"))
      errorMessages.foreach(message => logger.debug(s"Parsing error in $parsingMode: $message"))
    }
  }

  private class ErrorNodeCollector(
      private val errorNodes: mutable.Buffer[ErrorNode] = mutable.ListBuffer.empty)
      extends DefaultAstVisitor {

    override def enterErrorNode(errorNode: ErrorNode): Boolean = {
      errorNodes += errorNode
      true
    }

    def numberOfErrorNodes: Int = errorNodes.size

  }

  private def numberOfErrorNodes(ast: FunctionNode): Int = {
    val collector = new ErrorNodeCollector
    ast.accept(collector)
    collector.numberOfErrorNodes
  }

  private def buildParser(jsSource: JsSource, errorManager: Js2CpgErrMgr): Parser = {
    new Parser(ScriptEnvironment.builder().ecmaScriptVersion(ecmaVersion2020).build(),
               jsSource.source,
               errorManager)
  }

  private def nodeJsFix(jsSource: JsSource): JsSource = {
    val lines        = jsSource.source.getContent.toString.linesIterator.toSeq
    val replaceIndex = lines.lastIndexWhere(l => importRegex.matches(l.trim())) + 1
    val (head, rest) = lines.splitAt(replaceIndex)
    val fixedCode    = (head ++ nodeJsFixWrapper(rest)).mkString(System.lineSeparator())
    val source       = Source.sourceFor(jsSource.filePath, fixedCode)
    source.toJsSource(jsSource.srcDir, jsSource.projectDir)
  }

  private def safeParse(jsSource: JsSource): (FunctionNode, JsSource) = {
    val moduleErrorManager = new Js2CpgErrMgr("strict mode")
    buildParser(jsSource, moduleErrorManager).parseModule(moduleName) match {
      case null if moduleErrorManager.containsNodeJSInvalidReturn =>
        // We might have to fix NodeJS code here:
        val newSource = nodeJsFix(jsSource)
        val fixed     = buildParser(newSource, moduleErrorManager).parseModule(moduleName)
        if (fixed == null) {
          moduleErrorManager.printMessages()
          throw new ParserException(
            s"Parsing of file '${jsSource.filePath}' failed! See DEBUG logs.")
        } else {
          logger.debug(s"Applied NodeJS fix for file '${jsSource.filePath}'.")
          (fixed, newSource)
        }
      // if module parsing fails completely we have to fall-back to non-strict mode loosing imports/exports:
      case null =>
        val nonStrictErrorManager = new Js2CpgErrMgr("non-strict mode")
        buildParser(jsSource, nonStrictErrorManager).parse() match {
          // Sadly the parser catches all exception, passes them to its ErrorManager
          // and return null. In this case we throw again such an exception and reference to the logs.
          case null =>
            moduleErrorManager.printMessages()
            nonStrictErrorManager.printMessages()
            throw new ParserException(
              s"Parsing of file '${jsSource.filePath}' failed! See DEBUG logs.")
          case nonStrictAst =>
            logger.debug(s"Falling back to non-strict mode for file '${jsSource.filePath}'.")
            (nonStrictAst, jsSource)
        }
      // if this does not fail and does not contain ErrorNodes we are happy:
      case moduleAst if numberOfErrorNodes(moduleAst) == 0 => (moduleAst, jsSource)
      // if this does not fail but contains some ErrorNodes we compare its ErrorNode amount to non-strict parsing:
      case moduleAst =>
        val nonStrictErrorManager = new Js2CpgErrMgr("non-strict mode")
        buildParser(jsSource, nonStrictErrorManager).parse() match {
          case null =>
            (moduleAst, jsSource)
          case nonStrictAst if numberOfErrorNodes(nonStrictAst) < numberOfErrorNodes(moduleAst) =>
            logger.debug(s"Falling back to non-strict mode for file '${jsSource.filePath}'.")
            (nonStrictAst, jsSource)
          case _ =>
            (moduleAst, jsSource)
        }
    }
  }

  def parseFromSource(jsSource: JsSource): (FunctionNode, JsSource) = {
    safeParse(jsSource)
  }

  def parse(source: String, name: String = "unnamed"): (FunctionNode, JsSource) =
    parseFromSource(Source.sourceFor(name, source).toJsSource())
}

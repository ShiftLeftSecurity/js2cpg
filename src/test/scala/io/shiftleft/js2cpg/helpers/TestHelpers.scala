package io.shiftleft.js2cpg.helpers

import com.oracle.js.parser.Source
import com.oracle.js.parser.ir.FunctionNode
import io.shiftleft.js2cpg.parser.{JavaScriptParser, JsSource}
import io.shiftleft.js2cpg.util.SourceWrapper._

import scala.util.Using

// Put operations that are shared across different test cases here
object TestHelpers {

  private def parseInternal(src: JsSource): (JsSource, FunctionNode) =
    JavaScriptParser.parseFromSource(src).swap

  def parse(code: String): (JsSource, FunctionNode) = {
    val src = Source.sourceFor("test", code).toJsSource()
    parseInternal(src)
  }

  def parseFromUrl(fileUrl: String): (JsSource, FunctionNode) = {
    Using.resource(scala.io.Source.fromFile(fileUrl)) { content =>
      val code = content.getLines().mkString("\n")
      val src  = Source.sourceFor(fileUrl, code).toJsSource()
      parseInternal(src)
    }

  }

}

package io.shiftleft.js2cpg.parser

import com.oracle.js.parser.ir.Node
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class VisitorTest extends AnyWordSpec with Matchers {

  "Visitor" should {
    "should work correctly" in {
      val jsfunction =
        """
          |var express = require('express');
          |var app = express();
          |
          |app.get('/', function (req, res) {
          |  res.send('Hello World!');
          |});
          |
          |app.listen(3000, function () {
          |  console.log('Example app listening on port 3000!');
          |});
        """.stripMargin

      val ast           = JavaScriptParser.parse(jsfunction)._1
      val nodeCollector = new NodeCollectionVisitor
      ast.accept(nodeCollector)

      val nodeset = nodeCollector.nodes

      val nodeTester = new DefaultAstVisitor() {
        override def enterDefault(node: Node): Boolean = {
          assert(nodeset.contains(node))
          true
        }
      }
      ast.accept(nodeTester)
    }
  }
}

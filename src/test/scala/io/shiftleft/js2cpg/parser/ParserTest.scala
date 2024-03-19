package io.shiftleft.js2cpg.parser

import com.oracle.js.parser.ParserException
import com.oracle.js.parser.ir.{CallNode, FunctionNode}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ParserTest extends AnyWordSpec with Matchers {

  "Parsing" should {
    "fall back to non-strict mode" in {
      val jsfunction =
        """
          |#!/usr/bin/env node
          |var express = require('express');
          |import { serial as test } from 'ava';
        """.stripMargin

      val collector = new NodeCollectionVisitor
      val ast       = JavaScriptParser.parse(jsfunction)._1
      ast.accept(collector)
      val nodes = collector.nodes
      // things still get correctly parsed
      nodes.collect {
        case callNode: CallNode if callNode.getFunction.toString == "require" => callNode
      }.size shouldBe 1
      // but its no module any more
      ast.isModule shouldBe false
    }

    "throw a ParserException when parsing fails" in {
      val jsfunction =
        """
          |var x = "
        """.stripMargin

      a[ParserException] shouldBe thrownBy { JavaScriptParser.parse(jsfunction) }
    }

    "parse in module mode if possible" in {
      val jsfunction =
        """
          |var express = require('express');
          |import { serial as test } from 'ava';
        """.stripMargin

      val ast = JavaScriptParser.parse(jsfunction)._1
      // strict mode compliant stuff results in a module
      ast.isModule shouldBe true
      // with, e.g., imports in place
      ast.getModule.getImports.toString shouldBe "[import {serial as test} from \"ava\";]"
    }

    "work correctly for simple js program with newline" in {
      val jsfunction =
        """
          |if (err){
          |	   console.log("\n"+stderr);
          |		 res.end();
          |}
        """.stripMargin

      val collector    = new NodeCollectionVisitor
      val functionNode = JavaScriptParser.parse(jsfunction)._1
      functionNode.accept(collector)
      // note that root is a function node too
      collector.nodes.count(node => node.isInstanceOf[FunctionNode]) shouldBe 1
    }

    "work correctly for simple js program" in {
      val jsfunction =
        """
          |function myFunction() {
          |  document.getElementById("demo").innerHTML = "Hello World!";
          |}
          |myFunction();
        """.stripMargin

      val collector    = new NodeCollectionVisitor
      val functionNode = JavaScriptParser.parse(jsfunction)._1
      functionNode.accept(collector)
      // note that root is a function node too
      collector.nodes.count(node => node.isInstanceOf[FunctionNode]) shouldBe 2
    }

    "work correctly for node.js program" in {
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
      val collector    = new NodeCollectionVisitor
      val functionNode = JavaScriptParser.parse(jsfunction)._1
      functionNode.accept(collector)
      collector.nodes.count(node => node.isInstanceOf[FunctionNode]) shouldBe 3
    }

    "fix NodeJS invalid return" in {
      val jsfunction =
        "if(true) { return 1; }"
      val expected =
        "(function (exports, require, module, __filename, __dirname) { if(true) { return 1; } });"
      val jsSource = JavaScriptParser.parse(jsfunction)._2
      jsSource.source.getContent shouldBe expected
    }

    "fix NodeJS invalid return with import like code line" in {
      val jsfunction =
        """console.log('foobar');
          |return true;
          |// fooimportbar
          |console.log('fizzbuzz');""".stripMargin
      val expected =
        """(function (exports, require, module, __filename, __dirname) { console.log('foobar');
          |return true;
          |// fooimportbar
          |console.log('fizzbuzz'); });""".stripMargin
      val jsSource = JavaScriptParser.parse(jsfunction)._2
      jsSource.source.getContent shouldBe expected
    }

    "fix NodeJS invalid return with simple import" in {
      val jsfunction =
        """
          |import foo from 'dep.js';
          |if(true) { return 1; }""".stripMargin
      val expected =
        """
          |import foo from 'dep.js';
          |(function (exports, require, module, __filename, __dirname) { if(true) { return 1; } });""".stripMargin
      val jsSource = JavaScriptParser.parse(jsfunction)._2
      jsSource.source.getContent shouldBe expected
    }

    "fix NodeJS invalid return with multiple imports" in {
      val jsfunction =
        """
          |import name1 from "module-name";
          |import * as name2 from "module-name";
          |import { member1 } from "module-name";
          |import { member2 as alias1 } from "module-name";
          |import { member3 , member4 } from "module-name";
          |import { member5 , member6 as alias2 } from "module-name";
          |import defaultMember1, { member7 } from "module-name";
          |import defaultMember2, * as alias3 from "module-name";
          |import defaultMember3 from "module-name";
          |import "module-name";
          |if(true) { return 1; }""".stripMargin
      val expected =
        """
          |import name1 from "module-name";
          |import * as name2 from "module-name";
          |import { member1 } from "module-name";
          |import { member2 as alias1 } from "module-name";
          |import { member3 , member4 } from "module-name";
          |import { member5 , member6 as alias2 } from "module-name";
          |import defaultMember1, { member7 } from "module-name";
          |import defaultMember2, * as alias3 from "module-name";
          |import defaultMember3 from "module-name";
          |import "module-name";
          |(function (exports, require, module, __filename, __dirname) { if(true) { return 1; } });""".stripMargin
      val jsSource = JavaScriptParser.parse(jsfunction)._2
      jsSource.source.getContent shouldBe expected
    }

  }
}

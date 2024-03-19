package io.shiftleft.js2cpg.io

import better.files.File
import io.joern.x2cpg.X2Cpg.newEmptyCpg
import io.joern.x2cpg.utils.Report
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.passes.AbstractPassTest
import io.shiftleft.js2cpg.passes.AstCreationPass
import io.shiftleft.semanticcpg.language.*
import org.scalatest.Inside

class CodeDumperFromContentTests extends AbstractPassTest with Inside {

  "dumping code from content" should {
    implicit val finder: NodeExtensionFinder = DefaultNodeExtensionFinder

    val code = """
     |// A comment
     |function my_func(param1) {
     |  var x = foo(param1);
     |}""".stripMargin

    "allow one to dump a method node's source code from `File.contents`" in AstWithFileContentFixture(code) { cpg =>
      inside(cpg.method.nameExact("my_func").dumpRaw.l) {
        case content :: Nil =>
          pendingUntilFixed {
            // Need to enable JS in package io.shiftleft.semanticcpg.codedumper.CoreDumper.supportedLanguages
            content.linesIterator.map(_.strip).l shouldBe List(
              "func my_func(param1: Int) -> Int { /* <=== Test0.swift:<global>:my_func */",
              "let x: Int = foo(p: param1)",
              "}"
            )
          }
        case content => fail(s"Expected exactly 1 content dump, but got: $content")
      }
    }
  }

  "code from method content" should {
    val methodCode =
      """function my_func(param1) {
        |  var x = foo(param1);
        |}""".stripMargin
    val code =
      s"""
         |// A comment
         |$methodCode
         |""".stripMargin

    "allow one to dump a method node's source code from `Method.content`" in AstWithFileContentFixture(code) { cpg =>
      val List(content) = cpg.method.nameExact("my_func").content.l
      content shouldBe methodCode
    }
  }

  "code from typedecl content" should {
    val classCode =
      """class Foo {
        |  x = 'foo'
        |}""".stripMargin
    val code =
      s"""
         |// A comment
         |$classCode
         |""".stripMargin

    "allow one to dump a typedecl node's source code from `TypeDecl.content`" in AstWithFileContentFixture(code) {
      cpg =>
        val List(content) = cpg.typeDecl.nameExact("Foo").content.l
        content shouldBe classCode
    }
  }

  private object AstWithFileContentFixture extends Fixture {
    def apply(code: String)(f: Cpg => Unit): Unit = {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val file = dir / "code.js"
        file.write(code)
        val cpg       = newEmptyCpg()
        val filenames = List((file.path, file.parent.path))
        new AstCreationPass(
          cpg,
          filenames,
          Config().withInputPath(dir.toString).withDisableFileContent(false),
          new Report()
        ).createAndApply()
        f(cpg)
        file.delete()
      }
    }
  }

}
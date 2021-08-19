package io.shiftleft.js2cpg.io

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EmScriptenCleanerTest extends AnyWordSpec with Matchers {

  "EmScriptenCleaner" should {
    "do nothing if the code does not contain emscripten code" in {
      val code =
        """
        |console.log("Hello");
        |console.log("World!");""".stripMargin
      val result = EmScriptenCleaner.clean(code.linesIterator).mkString("\n")
      result shouldBe code
    }

    "do nothing if the code does not contain emscripten code but incomplete markers (start only)" in {
      val code =
        """
          |// EMSCRIPTEN_START_FUNCS
          |console.log("Hello");
          |console.log("World!");""".stripMargin
      val result = EmScriptenCleaner.clean(code.linesIterator).mkString("\n")
      result shouldBe code
    }

    "do nothing if the code does not contain emscripten code but incomplete markers (end only)" in {
      val code =
        """
           |console.log("Hello");
           |console.log("World!");
           |// EMSCRIPTEN_END_FUNCS""".stripMargin
      val result = EmScriptenCleaner.clean(code.linesIterator).mkString("\n")
      result shouldBe code
    }

    "do nothing if the code does not contain emscripten code but markers in the wrong order" in {
      val code =
        """
           |// EMSCRIPTEN_END_FUNCS
           |console.log("Hello");
           |console.log("World!");
           |// EMSCRIPTEN_START_FUNCS""".stripMargin
      val result = EmScriptenCleaner.clean(code.linesIterator).mkString("\n")
      result shouldBe code
    }

    "remove emscripten code lines" in {
      val code =
        """
           |main();
           |// EMSCRIPTEN_START_FUNCS
           |console.log("Hello");
           |console.log("World!");
           |// EMSCRIPTEN_END_FUNCS
           |otherCode();
           |foobar();""".stripMargin
      // should preserve new lines for correct line numbers
      val expected =
        """
          |main();
          |
          |
          |
          |
          |otherCode();
          |foobar();""".stripMargin
      val result = EmScriptenCleaner.clean(code.linesIterator).mkString("\n")
      result shouldBe expected
    }
  }

}

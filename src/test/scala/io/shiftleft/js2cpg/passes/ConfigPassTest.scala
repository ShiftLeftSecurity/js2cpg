package io.shiftleft.js2cpg.passes

import io.shiftleft.js2cpg.core.Report
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.semanticcpg.language._
import better.files.File
import io.shiftleft.js2cpg.io.FileDefaults
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ConfigPassTest extends AnyWordSpec with Matchers {

  "ConfigPass for Vue files" should {

    "generate ConfigFiles correctly for simply Vue project" in {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val fileA = dir / "a.vue"
        val fileB = dir / "b.vue"
        fileA.write("someCodeA();")
        fileB.write("someCodeB();")
        val cpg       = Cpg.emptyCpg
        val filenames = List((fileA.path, fileA.parent.path), (fileB.path, fileB.parent.path))
        new ConfigPass(filenames, cpg, new Report()).createAndApply()

        val List(configFileA) = cpg.configFile("a.vue").l
        val List(configFileB) = cpg.configFile("b.vue").l
        configFileA.content shouldBe "someCodeA();"
        configFileB.content shouldBe "someCodeB();"
      }
    }

  }

  "ConfigPass for other config files" should {

    "generate ConfigFiles correctly for simple JS project" in {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val fileA = dir / "a.conf.js"
        val fileB = dir / "b.config.js"
        val fileC = dir / "c.json"

        fileA.write("a")
        fileB.write("b")
        fileC.write("c")

        val cpg = Cpg.emptyCpg
        val filenames =
          List((fileA.path, fileA.parent.path), (fileB.path, fileB.parent.path), (fileC.path, fileC.parent.path))
        new ConfigPass(filenames, cpg, new Report()).createAndApply()

        val List(configFileA) = cpg.configFile("a.conf.js").l
        val List(configFileB) = cpg.configFile("b.conf.js").l
        val List(configFileC) = cpg.configFile("c.conf.js").l
        configFileA.content shouldBe "a"
        configFileB.content shouldBe "b"
        configFileC.content shouldBe "c"
      }
    }

    "skip ConfigFiles that are too large" in {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val fileA = dir / "a.json"
        val fileB = dir / "b.json"

        fileA.write("x\n" * FileDefaults.NUM_LINES_THRESHOLD + 1) // too many lines
        fileB.write("x" * FileDefaults.LINE_LENGTH_THRESHOLD + 1) // line too long

        val cpg       = Cpg.emptyCpg
        val filenames = List((fileA.path, fileA.parent.path), (fileB.path, fileB.parent.path))
        new ConfigPass(filenames, cpg, new Report()).createAndApply()

        cpg.configFile shouldBe empty
      }
    }

  }

  "ConfigPass for html files" should {

    "generate ConfigFiles correctly for simple JS project with html files" in {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val fileA = dir / "a.html"
        val fileB = dir / "b.html"

        fileA.write("a")
        fileB.write("b")

        val cpg       = Cpg.emptyCpg
        val filenames = List((fileA.path, fileA.parent.path), (fileB.path, fileB.parent.path))
        new ConfigPass(filenames, cpg, new Report()).createAndApply()

        val List(configFileA) = cpg.configFile("a.html").l
        val List(configFileB) = cpg.configFile("b.html").l
        configFileA.content shouldBe "a"
        configFileB.content shouldBe "b"
      }
    }

  }

  "PrivateKeyFilePass" should {

    "generate ConfigFiles correctly" in {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val fileA = dir / "a.key"
        fileA.write("-----BEGIN RSA PRIVATE KEY-----\n123456789\n-----END RSA PRIVATE KEY-----")
        val fileB = dir / "b.key"
        fileB.write("-----BEGIN SOME OTHER KEY-----\nthis is fine\n-----END SOME OTHER KEY-----")
        val cpg       = Cpg.emptyCpg
        val filenames = List((fileA.path, fileA.parent.path), (fileB.path, fileB.parent.path))
        new PrivateKeyFilePass(filenames, cpg, new Report()).createAndApply()

        val List(configFileA) = cpg.configFile("a.key").l
        configFileA.content shouldBe "Content omitted for security reasons."

        cpg.configFile("b.key") shouldBe empty
      }
    }

  }
}

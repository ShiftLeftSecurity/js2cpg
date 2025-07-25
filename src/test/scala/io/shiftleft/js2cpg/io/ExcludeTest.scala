package io.shiftleft.js2cpg.io

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.{Js2cpgArgumentsParser, Js2CpgMain}
import io.shiftleft.semanticcpg.language.*

import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

import java.util.regex.Pattern

object ExcludeTest {
  private implicit class ToArg(val arg: String) extends AnyVal {
    def toArg: String = s"--$arg"
  }
}

class ExcludeTest extends AnyWordSpec with Matchers with TableDrivenPropertyChecks {

  import ExcludeTest.*
  import Js2cpgArgumentsParser.*

  private val projectUnderTestPath = File(getClass.getResource("/excludes").toURI).pathAsString

  private def fileNames(cpg: Cpg): List[String] = cpg.file.name.l

  private def testWithArguments(
    args: Seq[String],
    expectedFiles: Set[String],
    defaultArgs: Set[String] = Set(NO_TS, NO_BABEL)
  ): Unit = {
    File.usingTemporaryDirectory("js2cpgTest") { tmpDir =>
      val cpgPath = tmpDir / "cpg.bin.zip"

      Js2CpgMain.main(
        Array(projectUnderTestPath, "--output", cpgPath.pathAsString) ++
          args.toArray ++
          defaultArgs.map(_.toArg).toArray
      )

      val cpg = Cpg.withStorage(cpgPath.pathAsString, deserializeOnClose = false)

      fileNames(cpg) should contain theSameElementsAs expectedFiles.map(_.replace("/", java.io.File.separator))
      cpg.close()
      cpgPath.deleteOnExit()
    }
  }

  "Using different excludes via program arguments" should {

    val testInput = Table(
      // -- Header for naming all test parameters
      ("statement", "arguments", "expectedResult"),
      // --
      // Test for default:
      (
        "exclude nothing if no excludes are given",
        Seq.empty[String],
        Set("index.js", "a.js", "folder/b.js", "folder/c.js", "foo.bar/d.js")
      ),
      // --
      // Tests for --exclude only:
      (
        s"exclude a file with ${EXCLUDE.toArg} with relative path",
        Seq(EXCLUDE.toArg, "index.js"),
        Set("a.js", "folder/b.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        s"exclude files with ${EXCLUDE.toArg} with relative paths",
        Seq(EXCLUDE.toArg, "index.js,folder/b.js"),
        Set("a.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        s"exclude a file with ${EXCLUDE.toArg} with absolute path",
        Seq(EXCLUDE.toArg, s"$projectUnderTestPath/index.js"),
        Set("a.js", "folder/b.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        s"exclude files with ${EXCLUDE.toArg} with absolute paths",
        Seq(EXCLUDE.toArg, s"$projectUnderTestPath/index.js,$projectUnderTestPath/folder/b.js"),
        Set("a.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        s"exclude files with ${EXCLUDE.toArg} with mixed paths",
        Seq(EXCLUDE.toArg, s"index.js,$projectUnderTestPath/folder/b.js"),
        Set("a.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        s"exclude a folder with ${EXCLUDE.toArg} with absolute path",
        Seq(EXCLUDE.toArg, s"$projectUnderTestPath/folder/"),
        Set("a.js", "index.js", "foo.bar/d.js")
      ),
      (
        s"exclude a folder with ${EXCLUDE.toArg} with relative path",
        Seq(EXCLUDE.toArg, s"folder/"),
        Set("a.js", "index.js", "foo.bar/d.js")
      ),
      // --
      // Tests for --exclude-regex only:
      (
        s"exclude a file with ${EXCLUDE_REGEX.toArg}",
        Seq(EXCLUDE_REGEX.toArg, ".*index\\..*"),
        Set("a.js", "folder/b.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        s"exclude files with ${EXCLUDE_REGEX.toArg}",
        Seq(EXCLUDE_REGEX.toArg, ".*(index|b)\\..*"),
        Set("a.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        s"exclude a complete folder with ${EXCLUDE_REGEX.toArg}",
        Seq(
          EXCLUDE_REGEX.toArg,
          s".*${Pattern.quote(java.io.File.separator)}folder${Pattern.quote(java.io.File.separator)}.*"
        ),
        Set("index.js", "a.js", "foo.bar/d.js")
      ),
      // --
      // Tests for mixed arguments
      (
        s"exclude files with ${EXCLUDE.toArg} and ${EXCLUDE_REGEX.toArg}",
        Seq(EXCLUDE.toArg, "a.js", EXCLUDE_REGEX.toArg, ".*(index|b)\\..*"),
        Set("folder/c.js", "foo.bar/d.js")
      ),
      // --
      // Tests for including test files
      (
        s"include test files with ${WITH_TESTS.toArg}",
        Seq(WITH_TESTS.toArg),
        Set(
          "index.js",
          "a.js",
          "folder/b.js",
          "folder/c.js",
          "foo.bar/d.js",
          "tests/a.spec.js",
          "tests/b.mock.js",
          "tests/c.e2e.js",
          "tests/d.test.js"
        )
      )
    )

    forAll(testInput) { (statement, arguments, result) =>
      s"$statement" in testWithArguments(arguments, result)
    }

  }

}

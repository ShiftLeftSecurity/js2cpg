package io.shiftleft.js2cpg.io

import better.files.File
import io.joern.x2cpg.X2Cpg.newEmptyCpg
import io.joern.x2cpg.utils.Report
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.JS_SUFFIX
import io.shiftleft.js2cpg.passes.AstCreationPass
import io.shiftleft.semanticcpg.language.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll

import java.util.regex.Pattern

class ExcludeTest extends AnyWordSpec with Matchers with TableDrivenPropertyChecks with BeforeAndAfterAll {

  private val testFiles: List[String] = List(
    ".sub/e.js",
    "folder/b.js",
    "folder/c.js",
    "foo.bar/d.js",
    "tests/a.spec.js",
    "tests/b.mock.js",
    "tests/c.e2e.js",
    "tests/d.test.js",
    "a.js",
    "b-min.js",
    "c.spec.js",
    "d.chunk.js",
    "index.js"
  )

  private val projectUnderTest: File = {
    val dir = File.newTemporaryDirectory("jssrc2cpgTestsExcludeTest")
    testFiles.foreach { testFile =>
      val file = dir / testFile
      file.createIfNotExists(createParents = true)
    }
    dir
  }

  override def afterAll(): Unit = projectUnderTest.delete(swallowIOExceptions = true)

  private def testWithArguments(
    exclude: Seq[String],
    excludeRegex: String,
    ignoreTests: Boolean,
    expectedFiles: Set[String]
  ): Unit = {
    File.usingTemporaryDirectory("js2cpgTests") { tmpDir =>
      val cpg = newEmptyCpg()
      val config = Config()
        .withInputPath(projectUnderTest.toString)
        .withOutputPath(tmpDir.toString)
        .withIgnoredFiles(exclude)
        .withIgnoredFilesRegex(excludeRegex)
        .withIgnoreTests(ignoreTests)
      val files = FileUtils
        .getFileTree(projectUnderTest.path, config, List(JS_SUFFIX))
        .map(f => (f, projectUnderTest.path))
      new AstCreationPass(cpg, files, config, new Report()).createAndApply()
      cpg.file.name.l should contain theSameElementsAs expectedFiles.map(_.replace("/", java.io.File.separator))
    }
  }

  "Using different excludes via program arguments" should {

    val testInput = Table(
      // -- Header for naming all test parameters
      ("statement", "--exclude", "--exclude-regex", "with-tests", "expectedResult"),
      // --
      // Test for default:
      (
        "exclude nothing if no excludes are given",
        Seq.empty[String],
        "",
        true,
        Set("index.js", "a.js", "folder/b.js", "folder/c.js", "foo.bar/d.js")
      ),
      // --
      // Tests for --exclude only:
      (
        "exclude a file with --exclude with relative path",
        Seq("index.js"),
        "",
        true,
        Set("a.js", "folder/b.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        "exclude files with --exclude with relative paths",
        Seq("index.js", "folder/b.js"),
        "",
        true,
        Set("a.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        "exclude a file with --exclude with absolute path",
        Seq(s"$projectUnderTest/index.js"),
        "",
        true,
        Set("a.js", "folder/b.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        "exclude files with --exclude with absolute paths",
        Seq(s"$projectUnderTest/index.js", s"$projectUnderTest/folder/b.js"),
        "",
        true,
        Set("a.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        "exclude files with --exclude with mixed paths",
        Seq("index.js", s"$projectUnderTest/folder/b.js"),
        "",
        true,
        Set("a.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        "exclude a folder with --exclude with absolute path",
        Seq(s"$projectUnderTest/folder/"),
        "",
        true,
        Set("a.js", "index.js", "foo.bar/d.js")
      ),
      (
        "exclude a folder with --exclude with relative path",
        Seq("folder/"),
        "",
        true,
        Set("a.js", "index.js", "foo.bar/d.js")
      ),
      // --
      // Tests for --exclude-regex only:
      (
        "exclude a file with --exclude-regex",
        Seq.empty,
        ".*index\\..*",
        true,
        Set("a.js", "folder/b.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        "exclude files with --exclude-regex",
        Seq.empty,
        ".*(index|b)\\..*",
        true,
        Set("a.js", "folder/c.js", "foo.bar/d.js")
      ),
      (
        "exclude a complete folder with --exclude-regex",
        Seq.empty,
        s".*${Pattern.quote(java.io.File.separator)}?folder${Pattern.quote(java.io.File.separator)}.*",
        true,
        Set("index.js", "a.js", "foo.bar/d.js")
      ),
      // --
      // Tests for mixed arguments
      (
        "exclude files with --exclude and --exclude-regex",
        Seq("a.js"),
        ".*(index|b)\\..*",
        true,
        Set("folder/c.js", "foo.bar/d.js")
      ),
      // --
      // Tests for including test files
      (
        "include test files with --with-tests",
        Seq.empty,
        "",
        false,
        Set(
          "index.js",
          "a.js",
          "folder/b.js",
          "folder/c.js",
          "foo.bar/d.js",
          "tests/a.spec.js",
          "tests/b.mock.js",
          "tests/c.e2e.js",
          "tests/d.test.js",
          "c.spec.js"
        )
      )
    )

    forAll(testInput) { (statement, exclude, excludeRegex, withTests, result) =>
      s"$statement" in testWithArguments(exclude, excludeRegex, withTests, result)
    }

  }

}

package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.core.Js2CpgMain
import io.shiftleft.js2cpg.io.FileDefaults.JS_SUFFIX
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.semanticcpg.language._
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import org.scalatest.wordspec.AnyWordSpec

@Slow
class TranspilationRunnerTest extends AnyWordSpec with Matchers {

  private def fileNames(cpg: Cpg): List[String] = cpg.file.name.l

  private def callLineNumbers(cpg: Cpg): List[Integer] = cpg.call.lineNumber.l

  private def callCodeFields(cpg: Cpg): List[String] = cpg.call.code.l

  private object TranspilationFixture {
    def apply(project: String)(f: File => Unit): Unit = {
      val projectPath = getClass.getResource(s"/$project").toURI
      File.usingTemporaryDirectory("js2cpgTest")(tmpDir => f(File(projectPath).copyToDirectory(tmpDir)))
    }
  }

  "TranspilationRunner" should {

    "generate js files correctly for a simple Babel project" in
      TranspilationFixture("babel") { tmpDir =>
        File.usingTemporaryDirectory("js2cpgTest") { transpileOutDir =>
          val config = Config().withInputPath(transpileOutDir.pathAsString).withTsTranspiling(false)

          new TranspilationRunner(tmpDir.path, transpileOutDir.path, config).execute()

          val transpiledJsFiles = FileUtils
            .getFileTree(transpileOutDir.path, config, List(JS_SUFFIX))
            .map(f => (f, transpileOutDir.path))

          val expectedJsFiles = List(((transpileOutDir / "foo.js").path, transpileOutDir.path))
          transpiledJsFiles should contain theSameElementsAs expectedJsFiles

          transpiledJsFiles
            .map(f => File(f._1).contentAsString.stripLineEnd)
            .mkString should endWith("//# sourceMappingURL=foo.js.map")
        }
      }

    "generate js files correctly for a simple Babel project in folder with whitespace" in
      TranspilationFixture("babel") { tmpDir =>
        File.usingTemporaryDirectory("js2cpgTest folder") { transpileOutDir =>
          val config = Config().withInputPath(transpileOutDir.pathAsString).withTsTranspiling(false)

          new TranspilationRunner(tmpDir.path, transpileOutDir.path, config).execute()

          val transpiledJsFiles = FileUtils
            .getFileTree(transpileOutDir.path, config, List(JS_SUFFIX))
            .map(f => (f, transpileOutDir.path))

          val expectedJsFiles = List(((transpileOutDir / "foo.js").path, transpileOutDir.path))
          transpiledJsFiles should contain theSameElementsAs expectedJsFiles

          transpiledJsFiles
            .map(f => File(f._1).contentAsString.stripLineEnd)
            .mkString should endWith("//# sourceMappingURL=foo.js.map")
        }
      }

    "contain correctly re-mapped code fields in simple Babel project" in
      TranspilationFixture("babel") { tmpDir =>
        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpDir.pathAsString, "--output", cpgPath.pathAsString, "--no-ts"))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain theSameElementsAs List("foo.js")
        callCodeFields(cpg) should contain allElementsOf List(
          "__ecma.Array.factory()",
          "_tmp_1 = __ecma.Array.factory()",
          "_tmp_1.push(1)",
          "_tmp_1.push",
          "_tmp_1.push(2)",
          "_tmp_1.push",
          "_tmp_1.push(3)",
          "_tmp_1.push",
          "(_tmp_0 = [1, 2, 3].map((n) => n + 1);)",
          "(_tmp_0 = [1, 2, 3].map((n) => n + 1);).map",
          "n + 1",
          "[1, 2, 3].map((n) => n + 1);.map(anonymous)"
        )

        cpg.close()
        cpgPath.deleteOnExit()
      }

    "generate and use sourcemap files correctly" in
      TranspilationFixture("typescript") { tmpDir =>
        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpDir.pathAsString, "--output", cpgPath.pathAsString, "--no-babel"))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain theSameElementsAs List("a.ts", "b.ts")
        callLineNumbers(cpg) should contain allElementsOf List(1, 1)

        cpg.close()
        cpgPath.deleteOnExit()
      }

    "generate js files correctly for a simple Typescript project" in
      TranspilationFixture("typescript") { tmpDir =>
        File.usingTemporaryDirectory("js2cpgTest") { transpileOutDir =>
          val config = Config().withInputPath(transpileOutDir.pathAsString).withTsTranspiling(false)

          val jsFiles = FileUtils
            .getFileTree(tmpDir.path, Config().withInputPath(tmpDir.pathAsString), List(JS_SUFFIX))
            .map(f => (f, tmpDir.path))

          val expectedJsFiles =
            List(
              ((transpileOutDir / "a.js").path, transpileOutDir.path),
              ((transpileOutDir / "b.js").path, transpileOutDir.path)
            )

          jsFiles.size shouldBe 0

          new TranspilationRunner(tmpDir.path, transpileOutDir.path, Config(babelTranspiling = false)).execute()

          val transpiledJsFiles = FileUtils
            .getFileTree(transpileOutDir.path, config, List(JS_SUFFIX))
            .map(f => (f, transpileOutDir.path))

          val jsFilesAfterTranspilation = jsFiles ++ transpiledJsFiles
          jsFilesAfterTranspilation should contain theSameElementsAs expectedJsFiles

          // all files should be transpiled
          every(
            jsFilesAfterTranspilation.map(f =>
              File(f._1).contentAsString
                .split("\n")
                .head // we ignore the sourcemap reference comment here
                .mkString
                .stripLineEnd
            )
          ) shouldBe "console.log(\"Hello World!\");"
        }
      }

    "generate js files correctly for a simple Typescript project with subfolders" in
      TranspilationFixture("typescriptsub") { tmpDir =>
        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpDir.pathAsString, "--output", cpgPath.pathAsString, "--no-babel"))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain theSameElementsAs List(
          "index.js",
          "main.ts",
          s"suba${java.io.File.separator}a.ts",
          s"suba${java.io.File.separator}b.ts",
          s"subb${java.io.File.separator}nested${java.io.File.separator}a.ts",
          s"subb${java.io.File.separator}nested${java.io.File.separator}b.ts",
          s"subb${java.io.File.separator}nested${java.io.File.separator}other.js",
          s"subc${java.io.File.separator}typescriptsub${java.io.File.separator}c.ts",
          s"subb${java.io.File.separator}a.ts",
          s"subb${java.io.File.separator}b.ts"
        )

        cpg.close()
        cpgPath.deleteOnExit()
      }

    "generate js files correctly for a simple Typescript project including test files" in
      TranspilationFixture("typescript") { tmpDir =>
        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpDir.pathAsString, "--output", cpgPath.pathAsString, "--no-babel", "--with-tests"))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain theSameElementsAs List(
          "a.ts",
          "b.ts",
          s"tests${java.io.File.separator}a.test.ts",
          s"tests${java.io.File.separator}b.spec.ts"
        )

        cpg.close()
        cpgPath.deleteOnExit()
      }

    "generate js files correctly for a simple multi-project Typescript project" in
      TranspilationFixture("multisimple") { tmpDir =>
        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpDir.pathAsString, "--output", cpgPath.pathAsString))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain theSameElementsAs List(
          "a.js",
          "b.ts",
          s"a${java.io.File.separator}a.ts",
          s"b${java.io.File.separator}b.js"
        )

        cpg.close()
        cpgPath.deleteOnExit()
      }

    "generate js files correctly for a multi-project Typescript project (using solution config)" in
      TranspilationFixture("multisolutionconfig") { tmpDir =>
        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpDir.pathAsString, "--output", cpgPath.pathAsString))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain theSameElementsAs List(
          "a.js",
          s"a${java.io.File.separator}a.ts",
          s"b${java.io.File.separator}b.js"
        )

        cpg.close()
        cpgPath.deleteOnExit()
      }

    "generate js files correctly for a simple Vue.js 2 project" in
      TranspilationFixture("vue2") { tmpDir =>
        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpDir.pathAsString, "--output", cpgPath.pathAsString))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain theSameElementsAs List(
          s"src${java.io.File.separator}main.js",
          s"src${java.io.File.separator}App.vue"
        )

        cpg.close()
        cpgPath.deleteOnExit()
      }

    "generate js files correctly for a simple Vue.js 3 project" in
      TranspilationFixture("vue3") { tmpDir =>
        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpDir.pathAsString, "--output", cpgPath.pathAsString))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain theSameElementsAs List(
          s"src${java.io.File.separator}views${java.io.File.separator}AboutPage.vue",
          s"src${java.io.File.separator}App.vue",
          s"src${java.io.File.separator}main.ts",
          s"src${java.io.File.separator}router${java.io.File.separator}index.ts"
        )

        cpg.close()
        cpgPath.deleteOnExit()
      }

    "generate js file correctly for a EJS template file" in
      TranspilationFixture("ejs") { tmpDir =>
        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpDir.pathAsString, "--output", cpgPath.pathAsString, "--no-ts", "--no-babel"))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain only "test.ejs"
        callLineNumbers(cpg) should contain allElementsOf List(5, 7, 15, 16, 21)

        cpg.close()
        cpgPath.deleteOnExit()
      }

    "generate js file correctly for a pug template file" in
      TranspilationFixture("pug") { tmpDir =>
        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpDir.pathAsString, "--output", cpgPath.pathAsString, "--no-ts", "--no-babel"))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        // Sadly, calling the pug transpiler via pug cli does not support source maps:
        fileNames(cpg) should contain only "test.js"
        callLineNumbers(cpg) should contain allElementsOf List(1, 2, 4, 7, 9)

        cpg.close()
        cpgPath.deleteOnExit()
      }

    "fail when running on engine restricted project" in TranspilationFixture("enginecheck") { tmpDir =>
      File.usingTemporaryDirectory("js2cpgTest") { transpileOutDir =>
        new TranspilationRunner(
          tmpDir.path,
          transpileOutDir.path,
          Config().withInputPath(tmpDir.pathAsString).withBabelTranspiling(false).withOptimizeDependencies(false)
        ).execute()
        val transpiledJsFiles =
          FileUtils.getFileTree(
            transpileOutDir.path,
            Config().withInputPath(transpileOutDir.pathAsString),
            List(JS_SUFFIX)
          )
        transpiledJsFiles shouldBe empty
      }
    }

    "work when running on engine restricted project with optimized dependencies" in TranspilationFixture(
      "enginecheck"
    ) { tmpDir =>
      File.usingTemporaryDirectory("js2cpgTest") { transpileOutDir =>
        new TranspilationRunner(
          tmpDir.path,
          transpileOutDir.path,
          Config().withInputPath(tmpDir.pathAsString).withBabelTranspiling(false).withOptimizeDependencies(true)
        ).execute()
        val transpiledJsFiles = FileUtils
          .getFileTree(transpileOutDir.path, Config().withInputPath(transpileOutDir.pathAsString), List(JS_SUFFIX))
          .map(_.getFileName.toString)
        transpiledJsFiles shouldBe List("index.js")
      }
    }

  }

}

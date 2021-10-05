package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.cpgloading.{CpgLoader, CpgLoaderConfig}
import io.shiftleft.codepropertygraph.generated.{NodeTypes, PropertyNames}
import io.shiftleft.js2cpg.core
import io.shiftleft.js2cpg.core.Js2CpgMain
import io.shiftleft.js2cpg.io.FileDefaults.{JS_SUFFIX, TS_SUFFIX}
import io.shiftleft.js2cpg.io.FileUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import org.scalatest.wordspec.AnyWordSpec
import overflowdb._
import overflowdb.traversal.TraversalSource

@Slow
class TranspilationRunnerTest extends AnyWordSpec with Matchers {

  private def fileNames(cpg: Cpg): List[String] = {
    val result =
      TraversalSource(cpg.graph).label(NodeTypes.FILE).property(PropertyNames.NAME).toList
    result.size should not be 0
    result
  }

  private def lineNumbers(cpg: Cpg, label: String = NodeTypes.CALL): List[Integer] = {
    val result =
      TraversalSource(cpg.graph).label(label).property(PropertyNames.LINE_NUMBER).toList
    result.size should not be 0
    result
  }

  "TranspilationRunner" should {

    "generate js files correctly for a simple Babel project" in {
      val projectPath = getClass.getResource("/babel").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        File.usingTemporaryDirectory() { transpileOutDir: File =>
          val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

          new TranspilationRunner(tmpProjectPath.path,
                                  transpileOutDir.path,
                                  core.Config(tsTranspiling = false)).execute()

          val transpiledJsFiles = FileUtils
            .getFileTree(transpileOutDir.path, core.Config(), JS_SUFFIX)
            .map(f => (f, transpileOutDir.path))

          val expectedJsFiles = List(((transpileOutDir / "foo.js").path, transpileOutDir.path))
          transpiledJsFiles should contain theSameElementsAs expectedJsFiles

          transpiledJsFiles
            .map(f => File(f._1).contentAsString.stripLineEnd)
            .mkString should endWith("//# sourceMappingURL=foo.js.map")
        }
      }
    }

    "generate and use sourcemap files correctly" in {
      val projectPath = getClass.getResource("/typescript").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(Array(tmpProjectPath.pathAsString, "--output", cpgPath, "--no-babel"))

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults.withOverflowConfig(
                Config.withDefaults.withStorageLocation(cpgPath)))
        fileNames(cpg) should contain theSameElementsAs List("a.ts", "b.ts")
        lineNumbers(cpg) should contain allElementsOf List(1, 1)
      }
    }

    "generate js files correctly for a simple Typescript project" in {
      val projectPath = getClass.getResource("/typescript").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        File.usingTemporaryDirectory() { transpileOutDir: File =>
          val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)
          val jsFiles = FileUtils
            .getFileTree(tmpProjectPath.path, core.Config(), JS_SUFFIX)
            .map(f => (f, tmpProjectPath.path))
          val tsFiles = FileUtils
            .getFileTree(tmpProjectPath.path, core.Config(), TS_SUFFIX)
            .map(f => (f, tmpProjectPath.path))

          val expectedTsFiles = List(((tmpProjectPath / "a.ts").path, tmpProjectPath.path),
                                     ((tmpProjectPath / "b.ts").path, tmpProjectPath.path))
          val expectedJsFiles =
            List(((transpileOutDir / "a.js").path, transpileOutDir.path),
                 ((transpileOutDir / "b.js").path, transpileOutDir.path))

          jsFiles.size shouldBe 0

          new TranspilationRunner(tmpProjectPath.path,
                                  transpileOutDir.path,
                                  core.Config(babelTranspiling = false)).execute()

          val transpiledJsFiles = FileUtils
            .getFileTree(transpileOutDir.path, core.Config(), JS_SUFFIX)
            .map(f => (f, transpileOutDir.path))

          val jsFilesAfterTranspilation = jsFiles ++ transpiledJsFiles
          jsFilesAfterTranspilation should contain theSameElementsAs expectedJsFiles

          // all files should be transpiled
          every(
            jsFilesAfterTranspilation.map(
              f =>
                File(f._1).contentAsString
                  .split(System.lineSeparator())
                  .head // we ignore the sourcemap reference comment here
                  .mkString
                  .stripLineEnd)) shouldBe "console.log(\"Hello World!\");"
        }
      }
    }

    "generate js files correctly for a simple Typescript project including test files" in {
      val projectPath = getClass.getResource("/typescript").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(tmpProjectPath.pathAsString, "--output", cpgPath, "--no-babel", "--with-tests"))

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults.withOverflowConfig(
                Config.withDefaults.withStorageLocation(cpgPath)))
        fileNames(cpg) should contain theSameElementsAs List(
          "a.ts",
          "b.ts",
          s"tests${java.io.File.separator}a.test.ts",
          s"tests${java.io.File.separator}b.spec.ts")
      }
    }

    "generate js files correctly for a simple multi-project Typescript project" in {
      val projectPath = getClass.getResource("/multi").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(Array(tmpProjectPath.pathAsString, "--output", cpgPath))

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults.withOverflowConfig(
                Config.withDefaults.withStorageLocation(cpgPath)))
        fileNames(cpg) should contain theSameElementsAs List("a.js",
                                                             s"a${java.io.File.separator}a.ts",
                                                             s"b${java.io.File.separator}b.js")
      }
    }

    "generate js files correctly for a simple Vue.js project" in {
      val projectPath = getClass.getResource("/vue").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(Array(tmpProjectPath.pathAsString, "--output", cpgPath, "--no-babel"))

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults.withOverflowConfig(
                Config.withDefaults.withStorageLocation(cpgPath)))
        fileNames(cpg) should contain theSameElementsAs List(s"src${java.io.File.separator}main.js",
                                                             s"src${java.io.File.separator}App.vue")
      }
    }

    "generate js file correctly for a EJS template file" in {
      val projectPath = getClass.getResource("/ejs").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(tmpProjectPath.pathAsString, "--output", cpgPath, "--no-ts", "--no-babel"))

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults.withOverflowConfig(
                Config.withDefaults.withStorageLocation(cpgPath)))
        fileNames(cpg) should contain only "test.ejs"
        lineNumbers(cpg) should contain allElementsOf List(5, 7, 15, 16, 21)
      }
    }

    "generate js file correctly for a pug template file" in {
      val projectPath = getClass.getResource("/pug").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(tmpProjectPath.pathAsString, "--output", cpgPath, "--no-ts", "--no-babel"))

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults.withOverflowConfig(
                Config.withDefaults.withStorageLocation(cpgPath)))
        // Sadly, calling the pug transpiler via pug cli does not support source maps:
        fileNames(cpg) should contain only "test.js"
        lineNumbers(cpg) should contain allElementsOf List(1, 2, 4, 7, 9)
      }
    }

  }

}

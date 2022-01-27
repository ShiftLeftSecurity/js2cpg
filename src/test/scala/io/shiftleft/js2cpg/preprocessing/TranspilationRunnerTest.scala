package io.shiftleft.js2cpg.preprocessing

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.cpgloading.{CpgLoader, CpgLoaderConfig}
import io.shiftleft.codepropertygraph.generated.{NodeTypes, PropertyNames}
import io.shiftleft.js2cpg.core
import io.shiftleft.js2cpg.core.Js2CpgMain
import io.shiftleft.js2cpg.io.FileDefaults.JS_SUFFIX
import io.shiftleft.js2cpg.io.FileUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import org.scalatest.wordspec.AnyWordSpec
import overflowdb._
import overflowdb.traversal._

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

  private def codeFields(cpg: Cpg, label: String = NodeTypes.CALL): List[Integer] = {
    val result =
      TraversalSource(cpg.graph).label(label).property(PropertyNames.CODE).toList
    result.size should not be 0
    result
  }

  private object TranspilationArchiveFixture {
    def apply(project: String, subProjects: Seq[String], executables: Seq[String])(
      f: File => Unit
    ): Unit = {
      val projectPath = getClass.getResource(s"/$project").toURI
      File.usingTemporaryDirectory() { tmpDir =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val allZips = subProjects.map(p =>
          (getClass.getResource(s"/$project/$p/node_modules.zip").toURI, tmpProjectPath / p)
        ) :+
          (getClass.getResource(s"/$project/node_modules.zip").toURI, tmpProjectPath)
        allZips.foreach { case (from, to) => File(from).unzipTo(destination = to) }

        val allExecutables = subProjects.flatMap(p =>
          executables.map(e => (tmpProjectPath / p / "node_modules" / ".bin" / e).toJava)
        ) ++
          executables.map(e => (tmpProjectPath / "node_modules" / ".bin" / e).toJava)
        allExecutables.foreach(_.setExecutable(true, false))

        f(tmpProjectPath)
      }
    }
  }

  private object TranspilationFixture {
    def apply(project: String)(f: File => Unit): Unit = {
      val projectPath = getClass.getResource(s"/$project").toURI
      File.usingTemporaryDirectory()(tmpDir => f(File(projectPath).copyToDirectory(tmpDir)))
    }
  }

  "TranspilationRunner" should {

    "generate js files correctly for a simple Babel project" in
      TranspilationArchiveFixture("babel", Seq.empty, Seq("babel")) { tmpDir =>
        File.usingTemporaryDirectory() { transpileOutDir =>
          new TranspilationRunner(
            tmpDir.path,
            transpileOutDir.path,
            core.Config(tsTranspiling = false, withNodeModuleFolder = true)
          ).execute()

          val transpiledJsFiles = FileUtils
            .getFileTree(transpileOutDir.path, core.Config(), List(JS_SUFFIX))
            .map(f => (f, transpileOutDir.path))

          val expectedJsFiles = List(((transpileOutDir / "foo.js").path, transpileOutDir.path))
          transpiledJsFiles should contain theSameElementsAs expectedJsFiles

          transpiledJsFiles
            .map(f => File(f._1).contentAsString.stripLineEnd)
            .mkString should endWith("//# sourceMappingURL=foo.js.map")
        }
      }

    "contain correctly re-mapped code fields in simple Babel project" in
      TranspilationArchiveFixture("babel", Seq.empty, Seq("babel")) { tmpDir =>
        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(tmpDir.pathAsString, "--output", cpgPath, "--no-ts", "--with-node-modules-folder")
        )

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults
                .withOverflowConfig(Config.withDefaults.withStorageLocation(cpgPath))
            )
        fileNames(cpg) should contain theSameElementsAs List("foo.js")
        codeFields(cpg) should contain allElementsOf List(
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
      }

    "generate and use sourcemap files correctly" in
      TranspilationArchiveFixture("typescript", Seq.empty, Seq("tsc")) { tmpDir =>
        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(
            tmpDir.pathAsString,
            "--output",
            cpgPath,
            "--no-babel",
            "--with-node-modules-folder"
          )
        )

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults
                .withOverflowConfig(Config.withDefaults.withStorageLocation(cpgPath))
            )
        fileNames(cpg) should contain theSameElementsAs List("a.ts", "b.ts")
        lineNumbers(cpg) should contain allElementsOf List(1, 1)
      }

    "generate js files correctly for a simple Typescript project" in
      TranspilationArchiveFixture("typescript", Seq.empty, Seq("tsc")) { tmpDir =>
        File.usingTemporaryDirectory() { transpileOutDir =>
          val jsFiles = FileUtils
            .getFileTree(tmpDir.path, core.Config(), List(JS_SUFFIX))
            .map(f => (f, tmpDir.path))

          val expectedJsFiles =
            List(
              ((transpileOutDir / "a.js").path, transpileOutDir.path),
              ((transpileOutDir / "b.js").path, transpileOutDir.path)
            )

          jsFiles.size shouldBe 0

          new TranspilationRunner(
            tmpDir.path,
            transpileOutDir.path,
            core.Config(babelTranspiling = false, withNodeModuleFolder = true)
          ).execute()

          val transpiledJsFiles = FileUtils
            .getFileTree(transpileOutDir.path, core.Config(), List(JS_SUFFIX))
            .map(f => (f, transpileOutDir.path))

          val jsFilesAfterTranspilation = jsFiles ++ transpiledJsFiles
          jsFilesAfterTranspilation should contain theSameElementsAs expectedJsFiles

          // all files should be transpiled
          every(
            jsFilesAfterTranspilation.map(f =>
              File(f._1).contentAsString
                .split(System.lineSeparator())
                .head // we ignore the sourcemap reference comment here
                .mkString
                .stripLineEnd
            )
          ) shouldBe "console.log(\"Hello World!\");"
        }
      }

    "generate js files correctly for a simple Typescript project with subfolders" in
      TranspilationArchiveFixture("typescriptsub", Seq.empty, Seq("tsc")) { tmpDir =>
        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(
            tmpDir.pathAsString,
            "--output",
            cpgPath,
            "--no-babel",
            "--with-node-modules-folder"
          )
        )

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults
                .withOverflowConfig(Config.withDefaults.withStorageLocation(cpgPath))
            )
        fileNames(cpg) should contain theSameElementsAs List(
          "index.js",
          "main.ts",
          s"suba${java.io.File.separator}a.ts",
          s"suba${java.io.File.separator}b.ts",
          s"subb${java.io.File.separator}nested${java.io.File.separator}a.ts",
          s"subb${java.io.File.separator}nested${java.io.File.separator}b.ts",
          s"subb${java.io.File.separator}nested${java.io.File.separator}other.js",
          s"subb${java.io.File.separator}a.ts",
          s"subb${java.io.File.separator}b.ts"
        )
      }

    "generate js files correctly for a simple Typescript project including test files" in
      TranspilationArchiveFixture("typescript", Seq.empty, Seq("tsc")) { tmpDir =>
        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(
            tmpDir.pathAsString,
            "--output",
            cpgPath,
            "--no-babel",
            "--with-tests",
            "--with-node-modules-folder"
          )
        )

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults
                .withOverflowConfig(Config.withDefaults.withStorageLocation(cpgPath))
            )
        fileNames(cpg) should contain theSameElementsAs List(
          "a.ts",
          "b.ts",
          s"tests${java.io.File.separator}a.test.ts",
          s"tests${java.io.File.separator}b.spec.ts"
        )
      }

    "generate js files correctly for a simple multi-project Typescript project" in
      TranspilationArchiveFixture("multisimple", Seq("a", "b"), Seq("tsc", "babel")) { tmpDir =>
        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(tmpDir.pathAsString, "--output", cpgPath, "--with-node-modules-folder")
        )

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults
                .withOverflowConfig(Config.withDefaults.withStorageLocation(cpgPath))
            )
        fileNames(cpg) should contain theSameElementsAs List(
          "a.js",
          "b.ts",
          s"a${java.io.File.separator}a.ts",
          s"b${java.io.File.separator}b.js"
        )
      }

    "generate js files correctly for a multi-project Typescript project (using solution config)" in
      TranspilationArchiveFixture("multisolutionconfig", Seq("a", "b"), Seq("tsc", "babel")) {
        tmpDir =>
          val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
          Js2CpgMain.main(
            Array(tmpDir.pathAsString, "--output", cpgPath, "--with-node-modules-folder")
          )

          val cpg =
            CpgLoader
              .loadFromOverflowDb(
                CpgLoaderConfig.withDefaults
                  .withOverflowConfig(Config.withDefaults.withStorageLocation(cpgPath))
              )
          fileNames(cpg) should contain theSameElementsAs List(
            "a.js",
            s"a${java.io.File.separator}a.ts",
            s"b${java.io.File.separator}b.js"
          )
      }

    "generate js files correctly for a simple Vue.js 2 project" in
      TranspilationArchiveFixture("vue2", Seq.empty, Seq("vue-cli-service", "tsc", "babel")) {
        tmpDir =>
          val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
          Js2CpgMain.main(
            Array(tmpDir.pathAsString, "--output", cpgPath, "--with-node-modules-folder")
          )

          val cpg =
            CpgLoader
              .loadFromOverflowDb(
                CpgLoaderConfig.withDefaults
                  .withOverflowConfig(Config.withDefaults.withStorageLocation(cpgPath))
              )
          fileNames(cpg) should contain theSameElementsAs List(
            s"src${java.io.File.separator}main.js",
            s"src${java.io.File.separator}App.vue"
          )
      }

    "generate js files correctly for a simple Vue.js 3 project" in
      TranspilationArchiveFixture("vue3", Seq.empty, Seq("vue-cli-service", "tsc", "babel")) {
        tmpDir =>
          val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
          Js2CpgMain.main(
            Array(tmpDir.pathAsString, "--output", cpgPath, "--with-node-modules-folder")
          )

          val cpg =
            CpgLoader
              .loadFromOverflowDb(
                CpgLoaderConfig.withDefaults
                  .withOverflowConfig(Config.withDefaults.withStorageLocation(cpgPath))
              )

          fileNames(cpg) should contain theSameElementsAs List(
            s"src${java.io.File.separator}views${java.io.File.separator}AboutPage.vue",
            s"src${java.io.File.separator}App.vue",
            s"src${java.io.File.separator}main.ts",
            s"src${java.io.File.separator}router${java.io.File.separator}index.ts"
          )
      }

    "generate js file correctly for a EJS template file" in
      TranspilationFixture("ejs") { tmpDir =>
        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(
            tmpDir.pathAsString,
            "--output",
            cpgPath,
            "--no-ts",
            "--no-babel",
            "--with-node-modules-folder"
          )
        )

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults
                .withOverflowConfig(Config.withDefaults.withStorageLocation(cpgPath))
            )
        fileNames(cpg) should contain only "test.ejs"
        lineNumbers(cpg) should contain allElementsOf List(5, 7, 15, 16, 21)
      }

    "generate js file correctly for a pug template file" in
      TranspilationArchiveFixture("pug", Seq.empty, Seq("pug")) { tmpDir =>
        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(
            tmpDir.pathAsString,
            "--output",
            cpgPath,
            "--no-ts",
            "--no-babel",
            "--with-node-modules-folder"
          )
        )

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults
                .withOverflowConfig(Config.withDefaults.withStorageLocation(cpgPath))
            )
        // Sadly, calling the pug transpiler via pug cli does not support source maps:
        fileNames(cpg) should contain only "test.js"
        lineNumbers(cpg) should contain allElementsOf List(1, 2, 4, 7, 9)
      }

  }

}

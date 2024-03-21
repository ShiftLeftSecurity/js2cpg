package io.shiftleft.js2cpg.passes

import better.files.File
import io.joern.x2cpg.X2Cpg.newEmptyCpg
import io.joern.x2cpg.utils.Report
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults
import io.shiftleft.js2cpg.parser.PackageJsonParser
import io.shiftleft.js2cpg.preprocessing.TypescriptTranspiler
import io.shiftleft.semanticcpg.language.*

class DependenciesPassTest extends AbstractPassTest {

  "DependenciesPass" should {

    "ignore empty package.json" in {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val json = dir / FileDefaults.PACKAGE_JSON_FILENAME
        json.write("")
        PackageJsonParser.isValidProjectPackageJson(json.path) shouldBe false
      }
    }

    "ignore package.json without any useful content" in {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val json = dir / FileDefaults.PACKAGE_JSON_FILENAME
        json.write("""
            |{
            |  "name": "something",
            |  "version": "0.1.0",
            |  "description": "foobar",
            |  "main": "./target_node/index.js",
            |  "private": true
            |}
            |""".stripMargin)
        PackageJsonParser.isValidProjectPackageJson(json.path) shouldBe false
      }
    }

    "ignore package.json without dependencies" in {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val json = dir / FileDefaults.PACKAGE_JSON_FILENAME
        json.write("{}")
        PackageJsonParser.isValidProjectPackageJson(json.path) shouldBe false
      }
    }

    "generate dependency nodes correctly (no dependencies at all)" in DependencyFixture("", "{}") { cpg =>
      getDependencies(cpg).size shouldBe 0
    }

    "generate dependency nodes correctly (empty dependency)" in DependencyFixture(
      "",
      """
        |{
        |  "dependencies": {
        |  }
        |}
        |""".stripMargin
    ) { cpg =>
      getDependencies(cpg).size shouldBe 0
    }

    "generate dependency nodes correctly (simple lock dependencies)" in DependencyFixture(
      code = "",
      jsonContent = """
          |{
          |  "dependencies": {
          |    "dep1": {
          |      "version": "0.1"
          |    },
          |    "dep2": {
          |      "version": "0.2"
          |    }
          |  }
          |}
          |""".stripMargin,
      jsonFilename = FileDefaults.JSON_LOCK_FILENAME
    ) { cpg =>
      val deps = getDependencies(cpg).l
      deps.size shouldBe 2
      deps.name("dep1").version("0.1").size shouldBe 1
      deps.name("dep2").version("0.2").size shouldBe 1
    }

    "generate dependency nodes correctly (simple fresh dependencies)" in DependencyFixture(
      code = "",
      jsons = Iterable(
        (
          "import_map.json",
          """{"imports": {
               |  "@/": "./",
               |  "$fresh/": "https://deno.land/x/fresh@1.1.0/",
               |  "$std/": "https://deno.land/std@0.152.0/",
               |  "gfm": "https://deno.land/x/gfm@0.1.22/mod.ts",
               |  "preact": "https://esm.sh/preact@10.10.6",
               |  "preact/": "https://esm.sh/preact@10.10.6/",
               |  "preact/signals": "https://esm.sh/*@preact/signals@1.0.3",
               |  "preact/signals-core": "https://esm.sh/*@preact/signals-core@1.0.1",
               |  "preact-render-to-string": "https://esm.sh/*preact-render-to-string@5.2.3/",
               |  "twind": "https://esm.sh/twind@0.16.17",
               |  "twind/": "https://esm.sh/twind@0.16.17/",
               |  "redis": "https://deno.land/x/redis@v0.26.0/mod.ts",
               |  "puppeteer": "https://deno.land/x/puppeteer@16.2.0/mod.ts",
               |  "envalid": "https://deno.land/x/envalid@0.1.2/mod.ts"
               |}}""".stripMargin
        ),
        (TypescriptTranspiler.DenoConfig, """{"importMap": "./import_map.json"}""")
      )
    ) { cpg =>
      val deps = getDependencies(cpg).l
      deps.size shouldBe 11
      deps.name("fresh").version("1.1.0").size shouldBe 1
      deps.name("std").version("0.152.0").size shouldBe 1
      deps.name("gfm").version("0.1.22").size shouldBe 1
      deps.name("preact").version("10.10.6").size shouldBe 1
      deps.name("preact/signals").version("1.0.3").size shouldBe 1
      deps.name("preact/signals-core").version("1.0.1").size shouldBe 1
      deps.name("preact-render-to-string").version("5.2.3").size shouldBe 1
      deps.name("twind").version("0.16.17").size shouldBe 1
      deps.name("redis").version("v0.26.0").size shouldBe 1
      deps.name("puppeteer").version("16.2.0").size shouldBe 1
      deps.name("envalid").version("0.1.2").size shouldBe 1
    }

    "generate dependency nodes correctly (simple dependency)" in DependencyFixture(
      code = "",
      jsonContent = """
          |{
          |  "dependencies": {
          |    "dep1": "0.1"
          |  }
          |}
          |""".stripMargin
    ) { cpg =>
      val deps = getDependencies(cpg).l
      deps.size shouldBe 1
      deps.name("dep1").version("0.1").size shouldBe 1
    }

    "generate dependency nodes correctly (different types of dependencies)" in DependencyFixture(
      code = "",
      jsonContent = """
        {
          "dependencies": {
            "dep1": "0.1"
          },
          "devDependencies": {
            "dep2": "0.2"
          },
          "peerDependencies": {
            "dep3": "0.3"
          },
          "optionalDependencies": {
            "dep4": "0.4"
          }
        }
        """.stripMargin
    ) { cpg =>
      val deps = getDependencies(cpg).l
      deps.size shouldBe 4
      deps.name("dep1").version("0.1").size shouldBe 1
      deps.name("dep2").version("0.2").size shouldBe 1
      deps.name("dep3").version("0.3").size shouldBe 1
      deps.name("dep4").version("0.4").size shouldBe 1
    }

  }

  private object DependencyFixture extends Fixture {
    def apply(code: String, jsons: Iterable[(String, String)])(f: Cpg => Unit): Unit = {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val file = dir / "file.js"
        file.write(code)

        val packageJson: String = jsons.head._1
        val jsonTmpFiles = jsons.map { case (jsonFilename, jsonContent) =>
          (dir / jsonFilename).write(jsonContent)
        }

        val filenames = List((file.path, file.parent.path))
        val cpg       = newEmptyCpg()
        val config    = Config().withInputPath(dir.toString).withPackageJsonLocation(packageJson)
        new AstCreationPass(cpg, filenames, config, new Report()).createAndApply()
        new DependenciesPass(cpg, config).createAndApply()

        f(cpg)
        jsonTmpFiles.foreach(_.delete())
        file.delete()
      }
    }

    def apply(code: String, jsonContent: String, jsonFilename: String = FileDefaults.PACKAGE_JSON_FILENAME)(
      f: Cpg => Unit
    ): Unit = {
      DependencyFixture(code, Iterable((jsonFilename, jsonContent)))(f)
    }
  }

}

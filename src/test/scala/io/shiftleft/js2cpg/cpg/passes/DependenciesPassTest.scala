package io.shiftleft.js2cpg.cpg.passes

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.PropertyNames
import io.shiftleft.js2cpg.core.{Config, Report}
import io.shiftleft.js2cpg.parser.PackageJsonParser
import io.shiftleft.js2cpg.preprocessing.TypescriptTranspiler
import overflowdb.traversal._

class DependenciesPassTest extends AbstractPassTest {

  "DependenciesPass" should {

    "ignore empty package.json" in {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val json = dir / PackageJsonParser.PACKAGE_JSON_FILENAME
        json.write("")
        PackageJsonParser.isValidProjectPackageJson(json.path) shouldBe false
      }
    }

    "ignore package.json without any useful content" in {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val json = dir / PackageJsonParser.PACKAGE_JSON_FILENAME
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
        val json = dir / PackageJsonParser.PACKAGE_JSON_FILENAME
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
      jsonFilename = PackageJsonParser.JSON_LOCK_FILENAME
    ) { cpg =>
      def deps = getDependencies(cpg)
      deps.size shouldBe 2
      deps.has(PropertyNames.NAME, "dep1").has(PropertyNames.VERSION, "0.1").size shouldBe 1
      deps.has(PropertyNames.NAME, "dep2").has(PropertyNames.VERSION, "0.2").size shouldBe 1
    }

    "generate dependency nodes correctly (simple fresh dependencies)" in DependencyFixture(
      code = "",
      jsons = Iterable(
        (
          "import_map.json",
          """
         |{
         |  "imports": {
         |    "@/": "./",
         |    "$fresh/": "https://deno.land/x/fresh@1.1.0/",
         |    "$std/": "https://deno.land/std@0.152.0/",
         |    "gfm": "https://deno.land/x/gfm@0.1.22/mod.ts",
         |    "preact": "https://esm.sh/preact@10.10.6",
         |    "preact/": "https://esm.sh/preact@10.10.6/",
         |    "preact/signals": "https://esm.sh/*@preact/signals@1.0.3",
         |    "preact/signals-core": "https://esm.sh/*@preact/signals-core@1.0.1",
         |    "preact-render-to-string": "https://esm.sh/*preact-render-to-string@5.2.3/",
         |    "twind": "https://esm.sh/twind@0.16.17",
         |    "twind/": "https://esm.sh/twind@0.16.17/",
         |    "redis": "https://deno.land/x/redis@v0.26.0/mod.ts",
         |    "puppeteer": "https://deno.land/x/puppeteer@16.2.0/mod.ts",
         |    "envalid": "https://deno.land/x/envalid@0.1.2/mod.ts"
         |  }
         |}
         |""".stripMargin
        ),
        (TypescriptTranspiler.DENO_CONFIG, """{"importMap": "./import_map.json"}""")
      )
    ) { cpg =>
      def deps = getDependencies(cpg)

      deps.size shouldBe 11
      deps.has(PropertyNames.NAME, "fresh").has(PropertyNames.VERSION, "1.1.0").size shouldBe 1
      deps.has(PropertyNames.NAME, "std").has(PropertyNames.VERSION, "0.152.0").size shouldBe 1
      deps.has(PropertyNames.NAME, "gfm").has(PropertyNames.VERSION, "0.1.22").size shouldBe 1
      deps.has(PropertyNames.NAME, "preact").has(PropertyNames.VERSION, "10.10.6").size shouldBe 1
      deps.has(PropertyNames.NAME, "preact/signals").has(PropertyNames.VERSION, "1.0.3").size shouldBe 1
      deps.has(PropertyNames.NAME, "preact/signals-core").has(PropertyNames.VERSION, "1.0.1").size shouldBe 1
      deps.has(PropertyNames.NAME, "preact-render-to-string").has(PropertyNames.VERSION, "5.2.3").size shouldBe 1
      deps.has(PropertyNames.NAME, "twind").has(PropertyNames.VERSION, "0.16.17").size shouldBe 1
      deps.has(PropertyNames.NAME, "redis").has(PropertyNames.VERSION, "v0.26.0").size shouldBe 1
      deps.has(PropertyNames.NAME, "puppeteer").has(PropertyNames.VERSION, "16.2.0").size shouldBe 1
      deps.has(PropertyNames.NAME, "envalid").has(PropertyNames.VERSION, "0.1.2").size shouldBe 1
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
      def deps = getDependencies(cpg)
      deps.size shouldBe 1
      deps.has(PropertyNames.NAME, "dep1").has(PropertyNames.VERSION, "0.1").size shouldBe 1
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
      def deps = getDependencies(cpg)
      deps.size shouldBe 4
      deps.has(PropertyNames.NAME, "dep1").has(PropertyNames.VERSION, "0.1").size shouldBe 1
      deps.has(PropertyNames.NAME, "dep2").has(PropertyNames.VERSION, "0.2").size shouldBe 1
      deps.has(PropertyNames.NAME, "dep3").has(PropertyNames.VERSION, "0.3").size shouldBe 1
      deps.has(PropertyNames.NAME, "dep4").has(PropertyNames.VERSION, "0.4").size shouldBe 1
    }

  }

  private object DependencyFixture extends Fixture {
    def apply(code: String, jsons: Iterable[(String, String)])(f: Cpg => Unit): Unit = {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val file = dir / "file.js"
        file.write(code)

        val packageJson: String = jsons.head._1
        for ((jsonFilename, jsonContent) <- jsons) {
          val json = dir / jsonFilename
          json.write(jsonContent)
        }

        val filenames = List((file.path, file.parent.path))
        val cpg       = Cpg.emptyCpg
        new AstCreationPass(dir, filenames, cpg, new Report()).createAndApply()
        new DependenciesPass(cpg, Config(srcDir = dir.toString, packageJsonLocation = packageJson)).createAndApply()

        f(cpg)
      }
    }

    def apply(code: String, jsonContent: String, jsonFilename: String = PackageJsonParser.PACKAGE_JSON_FILENAME)(
      f: Cpg => Unit
    ): Unit = {
      DependencyFixture(code, Iterable((jsonFilename, jsonContent)))(f)
    }
  }

}

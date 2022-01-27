package io.shiftleft.js2cpg.cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.PropertyNames
import better.files.File
import io.shiftleft.js2cpg.core.{Config, Report}
import io.shiftleft.js2cpg.parser.PackageJsonParser
import io.shiftleft.passes.IntervalKeyPool
import overflowdb.traversal._

class DependenciesPassTest extends AbstractPassTest {

  "DependenciesPass" should {

    "generate dependency nodes correctly (no dependencies at all)" in DependencyFixture("", "{}") {
      cpg =>
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
      packageJsonContent = """
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
      packageJsonName = PackageJsonParser.PACKAGE_JSON_LOCK_FILENAME
    ) { cpg =>
      def deps = getDependencies(cpg)
      deps.size shouldBe 2
      deps.has(PropertyNames.NAME, "dep1").has(PropertyNames.VERSION, "0.1").size shouldBe 1
      deps.has(PropertyNames.NAME, "dep2").has(PropertyNames.VERSION, "0.2").size shouldBe 1
    }

    "generate dependency nodes correctly (simple dependency)" in DependencyFixture(
      code = "",
      packageJsonContent = """
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
      packageJsonContent = """
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
    def apply(
      code: String,
      packageJsonContent: String,
      packageJsonName: String = PackageJsonParser.PACKAGE_JSON_FILENAME
    )(f: Cpg => Unit): Unit = {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val file = dir / "file.js"
        val json = dir / packageJsonName
        file.write(code)
        json.write(packageJsonContent)

        val cpg                 = Cpg.emptyCpg
        val keyPool             = new IntervalKeyPool(1001, 2000)
        val dependenciesKeyPool = new IntervalKeyPool(100, 1000100)
        val filenames           = List((file.path, file.parent.path))
        new AstCreationPass(dir, filenames, cpg, keyPool, new Report()).createAndApply()
        new DependenciesPass(
          cpg,
          Config(srcDir = dir.toString, packageJsonLocation = packageJsonName),
          dependenciesKeyPool
        ).createAndApply()

        f(cpg)
      }
    }
  }

}

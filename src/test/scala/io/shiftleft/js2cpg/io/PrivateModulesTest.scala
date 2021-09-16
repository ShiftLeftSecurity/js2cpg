package io.shiftleft.js2cpg.io

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.cpgloading.{CpgLoader, CpgLoaderConfig}
import io.shiftleft.codepropertygraph.generated.{PropertyNames, NodeTypes}
import io.shiftleft.js2cpg.core.Js2CpgMain
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import overflowdb._
import overflowdb.traversal.TraversalSource

class PrivateModulesTest extends AnyWordSpec with Matchers {

  private def fileNames(cpg: Cpg): List[String] = {
    val result =
      TraversalSource(cpg.graph).label(NodeTypes.FILE).property(PropertyNames.NAME).toList
    result.size should not be 0
    result
  }

  "Handling for private modules" should {

    "copy and generate js files correctly for a simple project" in {
      val projectPath = getClass.getResource("/privatemodules").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(Array(tmpProjectPath.pathAsString, "--output", cpgPath, "--no-babel"))

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults.withOverflowConfig(
                Config.withDefaults.withStorageLocation(cpgPath)))

        fileNames(cpg) should contain allElementsOf Set("@privateA/a.js", "@privateB/b.js")
      }
    }

    "copy and generate js files correctly for a simple project with additional private modules" in {
      val projectPath = getClass.getResource("/privatemodules").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(tmpProjectPath.pathAsString,
                "--output",
                cpgPath,
                "--no-babel",
                "--private-deps-ns",
                "privateC,privateD"))

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults.withOverflowConfig(
                Config.withDefaults.withStorageLocation(cpgPath)))

        fileNames(cpg) should contain allElementsOf Set("@privateA/a.js",
                                                        "@privateB/b.js",
                                                        "@privateC/c.js",
                                                        "privateD/d.js")
      }
    }

    "copy and generate js files correctly for a simple project with filter" in {
      val projectPath = getClass.getResource("/privatemodules").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(
          Array(tmpProjectPath.pathAsString,
                "--output",
                cpgPath,
                "--no-babel",
                "--exclude-regex",
                ".*@privateA/a.js"))

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults.withOverflowConfig(
                Config.withDefaults.withStorageLocation(cpgPath)))

        fileNames(cpg) should contain only "@privateB/b.js"
      }
    }

    "copy and generate js files correctly for a simple project with no private module being references" in {
      val projectPath = getClass.getResource("/ignoreprivatemodules").toURI
      File.usingTemporaryDirectory() { tmpDir: File =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(Array(tmpProjectPath.pathAsString, "--output", cpgPath, "--no-babel"))

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults.withOverflowConfig(
                Config.withDefaults.withStorageLocation(cpgPath)))

        fileNames(cpg) should contain only "index.js"
      }
    }

  }

}

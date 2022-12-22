package io.shiftleft.js2cpg.io

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.Js2CpgMain
import io.shiftleft.semanticcpg.language._

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.regex.Pattern

class PrivateModulesTest extends AnyWordSpec with Matchers {

  private def fileNames(cpg: Cpg): List[String] = cpg.file.name.l

  "Handling for private modules" should {

    "copy and generate js files correctly for a simple project" in {
      val projectPath = getClass.getResource("/privatemodules").toURI
      File.usingTemporaryDirectory("js2cpgTest") { tmpDir =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpProjectPath.pathAsString, "--output", cpgPath.pathAsString, "--no-babel"))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain allElementsOf Set(
          s"@privateA${java.io.File.separator}a.js",
          s"@privateB${java.io.File.separator}b.js"
        )
        cpg.close()
        cpgPath.deleteOnExit()
      }
    }

    "copy and generate js files correctly for a simple project with additional private modules" in {
      val projectPath = getClass.getResource("/privatemodules").toURI
      File.usingTemporaryDirectory("js2cpgTest") { tmpDir =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(
          Array(
            tmpProjectPath.pathAsString,
            "--output",
            cpgPath.pathAsString,
            "--no-babel",
            "--private-deps-ns",
            "privateC,privateD"
          )
        )

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain allElementsOf Set(
          s"@privateA${java.io.File.separator}a.js",
          s"@privateB${java.io.File.separator}b.js",
          s"@privateC${java.io.File.separator}c.js",
          s"privateD${java.io.File.separator}d.js"
        )
        cpg.close()
        cpgPath.deleteOnExit()
      }
    }

    "copy and generate js files correctly for a simple project with filter" in {
      val projectPath = getClass.getResource("/privatemodules").toURI
      File.usingTemporaryDirectory("js2cpgTest") { tmpDir =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(
          Array(
            tmpProjectPath.pathAsString,
            "--output",
            cpgPath.pathAsString,
            "--no-babel",
            "--exclude-regex",
            s".*@privateA${Pattern.quote(java.io.File.separator)}a.js"
          )
        )

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain only s"@privateB${java.io.File.separator}b.js"
        cpg.close()
        cpgPath.deleteOnExit()
      }
    }

    "copy and generate js files correctly for a simple project with no private module being references" in {
      val projectPath = getClass.getResource("/ignoreprivatemodules").toURI
      File.usingTemporaryDirectory("js2cpgTest") { tmpDir =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)

        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpProjectPath.pathAsString, "--output", cpgPath.pathAsString, "--no-babel"))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain only "index.js"
        cpg.close()
        cpgPath.deleteOnExit()
      }
    }

  }

}

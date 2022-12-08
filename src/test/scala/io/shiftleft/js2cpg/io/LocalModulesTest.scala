package io.shiftleft.js2cpg.io

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.Js2CpgMain
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.shiftleft.semanticcpg.language._
import org.scalatest.tags.Slow

@Slow
class LocalModulesTest extends AnyWordSpec with Matchers {

  private def fileNames(cpg: Cpg): List[String] = {
    val result = cpg.file.name.l
    result.size should not be 0
    result
  }

  "Handling for local modules" should {

    "generate js files correctly for a simple project" in {
      val projectPath = getClass.getResource("/localpaths").toURI
      val dep1        = getClass.getResource("/babel").toURI
      val dep2        = getClass.getResource("/typescript").toURI

      File.usingTemporaryDirectory("js2cpgTest") { tmpDir =>
        val tmpProjectPath = File(projectPath).copyToDirectory(tmpDir)
        File(dep1).copyToDirectory(tmpDir)
        File(dep2).copyToDirectory(tmpDir)

        val cpgPath = tmpDir / "cpg.bin.zip"
        Js2CpgMain.main(Array(tmpProjectPath.pathAsString, "--output", cpgPath.pathAsString))

        val cpg = Cpg.withConfig(overflowdb.Config.withoutOverflow.withStorageLocation(cpgPath.pathAsString))

        fileNames(cpg) should contain allElementsOf Set(
          s"babel${java.io.File.separator}foo.js",
          s"typescript${java.io.File.separator}a.ts",
          s"typescript${java.io.File.separator}b.ts"
        )
        cpg.close()
        cpgPath.deleteOnExit()
      }
    }
  }

}

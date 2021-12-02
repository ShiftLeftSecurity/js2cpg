package io.shiftleft.js2cpg.calllinker

import better.files.File
import io.shiftleft.codepropertygraph.cpgloading.CpgLoader
import io.shiftleft.codepropertygraph.cpgloading.CpgLoaderConfig
import io.shiftleft.js2cpg.core.Js2CpgMain
import io.shiftleft.semanticcpg.language.toMethodForCallGraph
import io.shiftleft.semanticcpg.language.toNodeTypeStarters
import io.shiftleft.semanticcpg.language.NoResolve
import io.shiftleft.semanticcpg.language.toCfgNodeMethods
import io.shiftleft.semanticcpg.language.toTraversal
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.Inside
import overflowdb._

class CallLinkerPassTest extends AnyWordSpec with Matchers with Inside {

  "CallLinkerPass" should {

    "create call edges correctly" in {
      File.usingTemporaryDirectory() { tmpDir: File =>
        val testFile = (tmpDir / "test.js").createFile()
        testFile.writeText("""
            |function sayhi() {
            |    console.log("Hello World!");
            |}
            |
            |sayhi();
            |""".stripMargin)

        val cpgPath = (tmpDir / "cpg.bin.zip").path.toString
        Js2CpgMain.main(Array(tmpDir.pathAsString, "--output", cpgPath, "--no-babel"))

        val cpg =
          CpgLoader
            .loadFromOverflowDb(
              CpgLoaderConfig.withDefaults.withOverflowConfig(
                Config.withDefaults.withStorageLocation(cpgPath)))

        inside(cpg.method("sayhi").l) {
          case List(m) =>
            m.name shouldBe "sayhi"
            m.code should endWith(".js::program:sayhi")
            m.fullName should endWith(".js::program:sayhi")
        }

        inside(cpg.method("sayhi").callIn(NoResolve).l) {
          case List(call) =>
            call.code shouldBe "sayhi()"
        }

      }
    }

  }

}

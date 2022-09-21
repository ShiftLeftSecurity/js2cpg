package io.shiftleft.js2cpg.calllinker

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.dataflow.DataFlowCode2CpgSuite
import io.shiftleft.semanticcpg.language._
import io.shiftleft.semanticcpg.language.NoResolve
import org.scalatest.Inside

class CallLinkerPassTest extends DataFlowCode2CpgSuite with Inside {

  "CallLinkerPass" should {
    val cpg: Cpg = code("""
        |function sayhi() {
        |  console.log("Hello World!");
        |}
        |
        |sayhi();
        |""".stripMargin)

    "create call edges correctly" in {
      inside(cpg.method("sayhi").l) { case List(m) =>
        m.name shouldBe "sayhi"
        m.fullName should endWith(".js::program:sayhi")
      }
      inside(cpg.method("sayhi").callIn(NoResolve).l) { case List(call) =>
        call.code shouldBe "sayhi()"
      }
    }
  }

}

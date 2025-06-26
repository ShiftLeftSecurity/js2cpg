package io.shiftleft.js2cpg.passes

import io.shiftleft.codepropertygraph.generated.{Cpg, Languages}
import io.shiftleft.semanticcpg.language.*

class JsMetaDataPassTest extends AbstractPassTest {

  "MetaDataPass" should {
    val cpg = Cpg.empty
    new JsMetaDataPass(cpg, "somehash", ".").createAndApply()

    "create exactly 1 node" in {
      cpg.graph.nodeCount shouldBe 1
    }

    "create no edges" in {
      cpg.graph.edgeCount shouldBe 0
    }

    "create a metadata node with correct language" in {
      cpg.metaData.language.l shouldBe List(Languages.JAVASCRIPT)
    }

    "create a metadata node with a hash" in {
      cpg.metaData.hash.l shouldBe List("somehash")
    }
  }

}

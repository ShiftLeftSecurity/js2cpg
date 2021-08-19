package io.shiftleft.js2cpg.cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.Languages
import io.shiftleft.passes.IntervalKeyPool
import io.shiftleft.semanticcpg.language._

import scala.jdk.CollectionConverters._

class JsMetaDataPassTest extends AbstractPassTest {

  "MetaDataPass" should {
    val cpg               = Cpg.emptyCpg
    val jsMetaDataKeyPool = new IntervalKeyPool(1, 100)

    new JsMetaDataPass(cpg, jsMetaDataKeyPool).createAndApply()

    "create exactly 1 node" in {
      cpg.graph.V.asScala.size shouldBe 1
    }

    "create no edges" in {
      cpg.graph.E.asScala.size shouldBe 0
    }

    "create a metadata node with correct language" in {
      cpg.metaData.language.l shouldBe List(Languages.JAVASCRIPT)
    }
  }

}

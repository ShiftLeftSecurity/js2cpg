package io.shiftleft.js2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.semanticcpg.language._

class BuiltinTypesPassTest extends AbstractPassTest {

  "BuiltinTypesPass" should {
    val cpg = Cpg.emptyCpg
    new BuiltinTypesPass(cpg).createAndApply()

    "create a '<global>' NamespaceBlock" in {
      cpg.namespaceBlock.name.l shouldBe List(Defines.GlobalNamespace)
    }

    "create types and type decls correctly" in {
      Defines.JsTypes.foreach { typeName =>
        val typeDeclNodes = cpg.typeDecl(typeName).l
        typeDeclNodes should have length 1
        val typeDeclNode = typeDeclNodes.head
        typeDeclNode.fullName shouldBe typeName
        typeDeclNode.isExternal shouldBe false
        typeDeclNode.filename shouldBe "builtintypes"

        cpg.namespaceBlock.astChildren.l should contain(typeDeclNode)

        val typeNodes = cpg.typ(typeName).l
        typeNodes should have length 1
        val typeNode = typeNodes.head
        typeNode.fullName shouldBe typeName
      }
    }

  }

}

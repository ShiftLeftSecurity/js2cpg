package io.shiftleft.js2cpg.cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.IntervalKeyPool
import io.shiftleft.semanticcpg.language._

class BuiltinTypesPassTest extends AbstractPassTest {

  "BuiltinTypesPass" should {
    val cpg                 = Cpg.emptyCpg
    val builtinTypesKeyPool = new IntervalKeyPool(1, 100)

    new BuiltinTypesPass(cpg, builtinTypesKeyPool).createAndApply()

    "create a '<global>' NamespaceBlock" in {
      cpg.namespaceBlock.name.l shouldBe List(Defines.GLOBAL_NAMESPACE)
    }

    "create types and type decls correctly" in {
      Defines.values.foreach {
        case typeName: Defines.Tpe =>
          val typeNameLabel = typeName.label

          val typeDeclNodes = cpg.typeDecl(typeNameLabel).l
          typeDeclNodes should have length 1
          val typeDeclNode = typeDeclNodes.head
          typeDeclNode.fullName shouldBe typeNameLabel
          typeDeclNode.isExternal shouldBe false
          typeDeclNode.filename shouldBe "builtintypes"

          cpg.namespaceBlock.astChildren.l should contain(typeDeclNode)

          val typeNodes = cpg.typ(typeNameLabel).l
          typeNodes should have length 1
          val typeNode = typeNodes.head
          typeNode.fullName shouldBe typeNameLabel
      }
    }

  }

}

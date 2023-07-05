package io.shiftleft.js2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{NewNamespaceBlock, NewType, NewTypeDecl}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeTypes}
import io.shiftleft.js2cpg.datastructures.OrderTracker
import io.shiftleft.passes.CpgPass
import org.slf4j.LoggerFactory

class BuiltinTypesPass(cpg: Cpg) extends CpgPass(cpg) {

  private val logger = LoggerFactory.getLogger(getClass)

  override def run(diffGraph: DiffGraphBuilder): Unit = {
    logger.debug("Generating builtin types.")

    val namespaceBlock = NewNamespaceBlock()
      .name(Defines.GlobalNamespace)
      .fullName(Defines.GlobalNamespace)

    diffGraph.addNode(namespaceBlock)

    val orderTracker = new OrderTracker()
    Defines.JsTypes.foreach { case typeName: String =>
      val tpe = NewType()
        .name(typeName)
        .fullName(typeName)
        .typeDeclFullName(typeName)
      diffGraph.addNode(tpe)

      val typeDecl = NewTypeDecl()
        .name(typeName)
        .fullName(typeName)
        .isExternal(false)
        .astParentType(NodeTypes.NAMESPACE_BLOCK)
        .astParentFullName(Defines.GlobalNamespace)
        .order(orderTracker.order)
        .filename("builtintypes")

      diffGraph.addNode(typeDecl)
      orderTracker.inc()
      diffGraph.addEdge(namespaceBlock, typeDecl, EdgeTypes.AST)
    }
  }

}

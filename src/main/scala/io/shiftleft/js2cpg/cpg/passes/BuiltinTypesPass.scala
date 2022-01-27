package io.shiftleft.js2cpg.cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeTypes}
import io.shiftleft.passes.{CpgPass, DiffGraph, KeyPool}
import io.shiftleft.codepropertygraph.generated.nodes.{NewNamespaceBlock, NewType, NewTypeDecl}
import io.shiftleft.js2cpg.cpg.datastructures.OrderTracker
import org.slf4j.LoggerFactory

class BuiltinTypesPass(cpg: Cpg, keyPool: KeyPool) extends CpgPass(cpg, keyPool = Some(keyPool)) {

  private val logger = LoggerFactory.getLogger(getClass)

  override def run(): Iterator[DiffGraph] = {
    logger.debug("Generating builtin types.")

    val diffGraph = DiffGraph.newBuilder

    val namespaceBlock = NewNamespaceBlock()
      .name(Defines.GLOBAL_NAMESPACE)
      .fullName(Defines.GLOBAL_NAMESPACE)

    diffGraph.addNode(namespaceBlock)

    val orderTracker = new OrderTracker()
    Defines.values.foreach { case typeName: Defines.Tpe =>
      val typeNameLabel = typeName.label

      val tpe = NewType()
        .name(typeNameLabel)
        .fullName(typeNameLabel)
        .typeDeclFullName(typeNameLabel)
      diffGraph.addNode(tpe)

      val typeDecl = NewTypeDecl()
        .name(typeNameLabel)
        .fullName(typeNameLabel)
        .isExternal(false)
        .astParentType(NodeTypes.NAMESPACE_BLOCK)
        .astParentFullName(Defines.GLOBAL_NAMESPACE)
        .order(orderTracker.order)
        .filename("builtintypes")

      diffGraph.addNode(typeDecl)
      orderTracker.inc()
      diffGraph.addEdge(namespaceBlock, typeDecl, EdgeTypes.AST)
    }

    Iterator(diffGraph.build())
  }

}

package io.shiftleft.js2cpg.astcreation

import io.shiftleft.codepropertygraph.generated.EdgeTypes
import io.shiftleft.codepropertygraph.generated.nodes._
import io.shiftleft.js2cpg.datastructures.OrderTracker
import overflowdb.BatchedUpdate.DiffGraphBuilder
import org.slf4j.LoggerFactory

class AstEdgeBuilder(private val diffGraph: DiffGraphBuilder) {

  private val logger = LoggerFactory.getLogger(getClass)

  private def addOrder(node: NewNode, order: Int): Unit = node match {
    case n: NewTypeDecl          => n.order = order
    case n: NewBlock             => n.order = order
    case n: NewCall              => n.order = order
    case n: NewFieldIdentifier   => n.order = order
    case n: NewFile              => n.order = order
    case n: NewIdentifier        => n.order = order
    case n: NewLocal             => n.order = order
    case n: NewMethod            => n.order = order
    case n: NewMethodParameterIn => n.order = order
    case n: NewMethodRef         => n.order = order
    case n: NewNamespaceBlock    => n.order = order
    case n: NewTypeRef           => n.order = order
    case n: NewUnknown           => n.order = order
    case n: NewModifier          => n.order = order
    case n: NewMethodReturn      => n.order = order
    case n: NewMember            => n.order = order
    case n: NewControlStructure  => n.order = order
    case n: NewLiteral           => n.order = order
    case n: NewReturn            => n.order = order
    case n: NewJumpTarget        => n.order = order
    case n                       => logger.warn(s"Unable to set ORDER for node: '$n'")
  }

  private def addArgumentIndex(node: NewNode, argIndex: Int): Unit = node match {
    case n: NewBlock            => n.argumentIndex = argIndex
    case n: NewCall             => n.argumentIndex = argIndex
    case n: NewFieldIdentifier  => n.argumentIndex = argIndex
    case n: NewIdentifier       => n.argumentIndex = argIndex
    case n: NewMethodRef        => n.argumentIndex = argIndex
    case n: NewTypeRef          => n.argumentIndex = argIndex
    case n: NewUnknown          => n.argumentIndex = argIndex
    case n: NewControlStructure => n.argumentIndex = argIndex
    case n: NewLiteral          => n.argumentIndex = argIndex
    case n: NewReturn           => n.argumentIndex = argIndex
    case n                      => logger.warn(s"Unable to set ARGUMENT_INDEX for node: '$n'")
  }

  def addAstEdge(dstId: NewNode, srcId: NewNode, orderTracker: OrderTracker): Unit = {
    dstId match {
      case wrapper: NewCompositeNode =>
        wrapper.iterate { id =>
          addAstEdge(id, srcId, orderTracker)
        }
      case impl: NewNode =>
        addAstEdge(impl, srcId)
        addOrder(impl, orderTracker.order)
        orderTracker.inc()
    }
  }

  def addAstEdge(dstId: NewNode, srcId: NewNode, order: Int): Unit = {
    val orderTracker = new OrderTracker(order)
    addAstEdge(dstId, srcId, orderTracker)
  }

  def addAstEdge(dstId: NewNode, srcId: NewNode): Unit = {
    diffGraph.addEdge(srcId, dstId, EdgeTypes.AST)
  }

  def addConditionEdge(dstId: NewNode, srcId: NewNode): Unit = {
    diffGraph.addEdge(srcId, dstId, EdgeTypes.CONDITION)
  }

  def addReceiverEdge(dstId: NewNode, srcId: NewNode): Unit = {
    diffGraph.addEdge(srcId, dstId, EdgeTypes.RECEIVER)
  }

  def addRefEdge(dstId: NewNode, srcId: NewNode): Unit = {
    diffGraph.addEdge(srcId, dstId, EdgeTypes.REF)
  }

  def addCaptureEdge(dstId: NewNode, srcId: NewNode): Unit = {
    diffGraph.addEdge(srcId, dstId, EdgeTypes.CAPTURE)
  }

  def addBindsEdge(dstId: NewNode, srcId: NewNode): Unit = {
    diffGraph.addEdge(srcId, dstId, EdgeTypes.BINDS)
    addOrder(srcId, 0)
  }

  def addArgumentEdge(dstId: NewNode, srcId: NewNode): Unit = {
    diffGraph.addEdge(srcId, dstId, EdgeTypes.ARGUMENT)
  }

  def addArgumentEdge(dstId: NewNode, srcId: NewNode, orderTracker: OrderTracker): Unit = {
    diffGraph.addEdge(srcId, dstId, EdgeTypes.ARGUMENT)
    addArgumentIndex(dstId, orderTracker.order)
    orderTracker.inc()
  }

  def addArgumentEdge(dstId: NewNode, srcId: NewNode, argIndex: Int): Unit = {
    diffGraph.addEdge(srcId, dstId, EdgeTypes.ARGUMENT)
    addArgumentIndex(dstId, argIndex)
  }

}

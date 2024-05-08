package io.shiftleft.js2cpg.astcreation

import flatgraph.BatchedUpdateInterface
import io.shiftleft.codepropertygraph.generated.nodes.{NewNode, StoredNode}

import java.util
import scala.collection.mutable.ListBuffer

class NewCompositeNode(underlying: ListBuffer[NewNode] = ListBuffer.empty[NewNode]) extends NewNode(nodeKind = -1) {
  override def label: String = "COMPOSITE"

  override def canEqual(that: Any): Boolean =
    that.isInstanceOf[NewCompositeNode]

  override def productArity: Int = 0

  override def productElement(n: Int): Any = null

  override def copy(): this.type = {
    val newInstance = new NewCompositeNode(underlying.clone())
    newInstance.asInstanceOf[this.type]
  }

  def add(newNode: NewNode): Unit = {
    underlying.append(newNode)
  }

  def iterate[T](func: NewNode => T): Unit = {
    underlying.foreach(func)
  }

  override def isValidInNeighbor(edgeLabel: String, node: NewNode): Boolean  = ??? // we do not need this
  override def isValidOutNeighbor(edgeLabel: String, node: NewNode): Boolean = ??? // we do not need this
  override def propertiesMap: util.Map[String, Any]                          = ??? // we do not need this
  override type StoredNodeType = StoredNode
  override def flattenProperties(interface: BatchedUpdateInterface): Unit = ???
}

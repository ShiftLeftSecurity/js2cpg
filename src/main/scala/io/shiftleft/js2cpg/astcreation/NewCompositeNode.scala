package io.shiftleft.js2cpg.astcreation

import io.shiftleft.codepropertygraph.generated.nodes.NewNode

import scala.collection.mutable.ListBuffer

class NewCompositeNode(underlying: ListBuffer[NewNode] = ListBuffer.empty[NewNode]) extends NewNode {
  override def label: String = "COMPOSITE"

  override def properties: Map[String, Any] = ??? // we do not need this

  override def canEqual(that: Any): Boolean =
    that.isInstanceOf[NewCompositeNode]

  override def productArity: Int = 0

  override def productElement(n: Int): Any = null

  override def copy: this.type = {
    val newInstance = new NewCompositeNode(underlying.clone())
    newInstance.asInstanceOf[this.type]
  }

  def add(newNode: NewNode): Unit = {
    underlying.append(newNode)
  }

  def iterate[T](func: NewNode => T): Unit = {
    underlying.foreach(func)
  }

  def isValidInNeighbor(edgeLabel: String, node: NewNode): Boolean = ??? // we do not need this

  def isValidOutNeighbor(edgeLabel: String, node: NewNode): Boolean = ??? // we do not need this

}

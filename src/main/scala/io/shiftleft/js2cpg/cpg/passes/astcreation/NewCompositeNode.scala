package io.shiftleft.js2cpg.cpg.passes.astcreation

import io.shiftleft.codepropertygraph.generated.nodes.NewNode

import scala.collection.mutable.ListBuffer

class NewCompositeNode(underlying: ListBuffer[NewNode] = ListBuffer.empty[NewNode])
    extends NewNode {
  override def label: String = "COMPOSITE"

  override def properties: Map[String, Any] = ??? // we do not need this

  def add(newNode: NewNode): Unit = {
    underlying.append(newNode)
  }

  def iterate[T](func: NewNode => T): Unit = {
    underlying.foreach(func)
  }
}

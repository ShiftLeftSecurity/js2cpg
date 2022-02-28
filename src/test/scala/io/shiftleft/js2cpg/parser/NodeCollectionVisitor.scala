package io.shiftleft.js2cpg.parser

import com.oracle.js.parser.ir.Node

import scala.collection.mutable

class NodeCollectionVisitor(val nodes: mutable.Buffer[Node] = mutable.ListBuffer.empty) extends DefaultAstVisitor {

  override def enterDefault(node: Node): Boolean = {
    nodes += node
    true
  }

}

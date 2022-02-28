package io.shiftleft.js2cpg.cpg.passes

import com.oracle.js.parser.ir.IdentNode
import io.shiftleft.js2cpg.parser.DefaultAstVisitor

import scala.collection.mutable

class UsedIdentNodesPass(val usedIdentNodes: mutable.Set[String] = mutable.HashSet.empty) extends DefaultAstVisitor {

  override def enterIdentNode(identNode: IdentNode): Boolean = {
    usedIdentNodes.add(identNode.getName)
    false
  }

}

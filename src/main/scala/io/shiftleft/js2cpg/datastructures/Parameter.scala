package io.shiftleft.js2cpg.datastructures

import com.oracle.js.parser.ir.{IdentNode, Statement}

case class Parameter(components: List[IdentNode], initExpression: Option[Statement])

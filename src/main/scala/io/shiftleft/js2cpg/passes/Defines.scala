package io.shiftleft.js2cpg.passes

import io.shiftleft.semanticcpg.language.types.structure.NamespaceTraversal

object Defines {
  val Any: String     = "ANY"
  val Number: String  = "__ecma.Number"
  val String: String  = "__ecma.String"
  val Boolean: String = "__ecma.Boolean"
  val Null: String    = "__ecma.Null"
  val Math: String    = "__ecma.Math"
  val Console: String = "__whatwg.console"

  val GlobalNamespace: String = NamespaceTraversal.globalNamespaceName

  val JsTypes: List[String] =
    List(Any, Number, String, Boolean, Null, Math, Console)
}

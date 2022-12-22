package io.shiftleft.js2cpg.passes

class DefineTypes extends Enumeration {
  type Defines = Value

  val ANY: Tpe     = Tpe("ANY")
  val NUMBER: Tpe  = Tpe("__ecma.Number")
  val STRING: Tpe  = Tpe("__ecma.String")
  val BOOLEAN: Tpe = Tpe("__ecma.Boolean")
  val NULL: Tpe    = Tpe("__ecma.Null")
  val MATH: Tpe    = Tpe("__ecma.Math")
  val CONSOLE: Tpe = Tpe("__whatwg.console")

  val GLOBAL_NAMESPACE = "<global>"

  class Tpe(val label: String) extends super.Val
  private object Tpe {
    def apply(label: String): Tpe = new Tpe(label)
  }
}

object Defines extends DefineTypes

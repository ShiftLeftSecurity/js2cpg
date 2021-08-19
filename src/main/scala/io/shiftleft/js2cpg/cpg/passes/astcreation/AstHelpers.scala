package io.shiftleft.js2cpg.cpg.passes.astcreation

import com.oracle.js.parser.TokenType
import io.shiftleft.codepropertygraph.generated.Operators

object AstHelpers {
  def getBinaryOperation(op: TokenType): String = op match {
    // arithmetic
    case TokenType.ADD => Operators.addition
    case TokenType.SUB => Operators.subtraction
    case TokenType.MUL => Operators.multiplication
    case TokenType.DIV => Operators.division
    case TokenType.MOD => Operators.modulo
    case TokenType.EXP => Operators.exponentiation
    // comparison
    case TokenType.LT         => Operators.lessThan
    case TokenType.GT         => Operators.greaterThan
    case TokenType.LE         => Operators.lessEqualsThan
    case TokenType.GE         => Operators.greaterEqualsThan
    case TokenType.EQ         => Operators.equals
    case TokenType.EQ_STRICT  => Operators.equals
    case TokenType.NE         => Operators.notEquals
    case TokenType.NE_STRICT  => Operators.notEquals
    case TokenType.INSTANCEOF => Operators.instanceOf
    // logical
    case TokenType.AND => Operators.logicalAnd
    case TokenType.OR  => Operators.logicalOr
    case TokenType.NOT => Operators.logicalNot
    // bitwise
    case TokenType.BIT_AND => Operators.and
    case TokenType.BIT_OR  => Operators.or
    case TokenType.BIT_NOT => Operators.not
    case TokenType.BIT_XOR => Operators.xor
    case TokenType.SHL     => Operators.shiftLeft
    case TokenType.SAR     => Operators.arithmeticShiftRight
    case TokenType.SHR     => Operators.logicalShiftRight
    // assignment operators
    case TokenType.ASSIGN         => Operators.assignment
    case TokenType.ASSIGN_INIT    => Operators.assignment
    case TokenType.ASSIGN_ADD     => Operators.assignmentPlus
    case TokenType.ASSIGN_SUB     => Operators.assignmentMinus
    case TokenType.ASSIGN_MUL     => Operators.assignmentMultiplication
    case TokenType.ASSIGN_DIV     => Operators.assignmentDivision
    case TokenType.ASSIGN_MOD     => Operators.assignmentModulo
    case TokenType.ASSIGN_EXP     => Operators.assignmentExponentiation
    case TokenType.ASSIGN_BIT_AND => Operators.assignmentAnd
    case TokenType.ASSIGN_BIT_OR  => Operators.assignmentOr
    case TokenType.ASSIGN_BIT_XOR => Operators.assignmentXor
    case TokenType.ASSIGN_SHL     => Operators.assignmentShiftLeft
    case TokenType.ASSIGN_SAR     => Operators.assignmentArithmeticShiftRight
    case TokenType.ASSIGN_SHR     => Operators.assignmentLogicalShiftRight
    // others
    case TokenType.COMMARIGHT     => "<operator>.commaright"
    case TokenType.COMMALEFT      => "<operator>.commaleft"
    case TokenType.IN             => "<operator>.in"
    case TokenType.NULLISHCOALESC => "<operator>.nullishcoalesc"
    // TODO: this list is still incomplete:
    case other => throw new NotImplementedError(s"TokenType '$other' not yet supported!")
  }

  def getUnaryOperation(op: String): String = op match {
    case "new"             => "constructor"
    case "!"               => Operators.logicalNot
    case "++"              => Operators.preIncrement
    case "--"              => Operators.preDecrement
    case "-"               => Operators.minus
    case "+"               => Operators.plus
    case "~"               => "<operator>.bitNot"
    case "incpostfix"      => Operators.postIncrement
    case "decpostfix"      => Operators.postDecrement
    case "typeof"          => Operators.instanceOf
    case "delete"          => Operators.delete
    case "yield"           => "<operator>.yield"
    case "yield_star"      => "<operator>.yieldStar"
    case "ident"           => "<operator>.ident"
    case "void"            => "<operator>.void"
    case "await"           => "<operator>.await"
    case "string"          => "<operator>.string"
    case "spread_array"    => "<operator>.spreadArray"
    case "spread_object"   => "<operator>.spreadObject"
    case "spread_argument" => "<operator>.spreadArgument"
    // TODO: this list is still incomplete:
    case other => throw new NotImplementedError(s"Unary operator '$other' not yet supported!")
  }
}

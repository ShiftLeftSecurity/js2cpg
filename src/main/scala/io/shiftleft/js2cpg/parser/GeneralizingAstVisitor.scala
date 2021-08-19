package io.shiftleft.js2cpg.parser

import com.oracle.js.parser.ir._
import com.oracle.js.parser.ir.visitor.TranslatorNodeVisitor

/**
  * This visitor mapped the enterXXX methods to the visit() to provide
  * an interface which is closer to the original pattern.
  * Furthermore, a not overridden visit() implementation calls the visit()
  * method for the next type in the class hierarchy to allow using
  * code to handle certain AST parts in a generalized way on any part
  * of the class hierarchy.
  */
class GeneralizingAstVisitor[T]
    extends TranslatorNodeVisitor[LexicalContext, T](new LexicalContext()) {

  protected val globalBuiltins =
    Set(
      "Buffer.from",
      "decodeURI",
      "decodeURIComponent",
      "encodeURI",
      "encodeURIComponent",
      "eval",
      "fetch",
      "isFinite",
      "isNaN",
      "JSON.parse",
      "JSON.stringify",
      "Number.isFinite",
      "Number.isInteger",
      "Number.isNaN",
      "Number.isSafeInteger",
      "Number.parseFloat",
      "Number.parseInt",
      "Number.prototype.toExponential",
      "Number.prototype.toFixed",
      "Number.prototype.toLocaleString",
      "Number.prototype.toPrecision",
      "Number.prototype.toSource",
      "Number.prototype.toString",
      "Number.prototype.valueOf",
      "parseFloat",
      "parseInt",
      "uneval",
      "Object.assign",
      "Object.create",
      "Object.defineProperties",
      "Object.defineProperty",
      "Object.entries",
      "Object.freeze",
      "Object.fromEntries",
      "Object.getOwnPropertyDescriptor",
      "Object.getOwnPropertyDescriptors",
      "Object.getOwnPropertyNames",
      "Object.getOwnPropertySymbols",
      "Object.getPrototypeOf",
      "Object.is",
      "Object.isExtensible",
      "Object.isFrozen",
      "Object.isSealed",
      "Object.keys",
      "Object.preventExtensions",
      "Object.prototype.__defineGetter__",
      "Object.prototype.__defineSetter__",
      "Object.prototype.__lookupGetter__",
      "Object.prototype.__lookupSetter__",
      "Object.prototype.hasOwnProperty",
      "Object.prototype.isPrototypeOf",
      "Object.prototype.propertyIsEnumerable",
      "Object.prototype.toLocaleString",
      "Object.prototype.toSource",
      "Object.prototype.toString",
      "Object.prototype.valueOf",
      "Object.seal",
      "Object.setPrototypeOf",
      "Object.values",
      "Promise.all",
      "Promise.allSettled",
      "Promise.any",
      "Promise.race",
      "Promise.reject",
      "Promise.resolve",
      "localStorage.setItem",
    )

  def visit(node: Node): T = {
    throw new AssertionError(
      String.format("should not reach here. %s(%s)", node.getClass.getSimpleName, node))
  }

  def visit(node: AccessNode): T = {
    visit(node.asInstanceOf[BaseNode])
  }

  def visit(node: BaseNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: Block): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: BinaryNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: BreakNode): T = {
    visit(node.asInstanceOf[JumpStatement])
  }

  def visit(node: CallNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: CaseNode): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: CatchNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: ContinueNode): T = {
    visit(node.asInstanceOf[JumpStatement])
  }

  def visit(node: DebuggerNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: EmptyNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: ErrorNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: NamedExportsNode): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: ExportNode): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: ExportSpecifierNode): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: Expression): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: ExpressionStatement): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: BlockStatement): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: ForNode): T = {
    visit(node.asInstanceOf[LoopNode])
  }

  def visit(node: FromNode): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: FunctionNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: IdentNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: IfNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: ImportClauseNode): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: ImportNode): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: ImportSpecifierNode): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: IndexNode): T = {
    visit(node.asInstanceOf[BaseNode])
  }

  def visit(node: JumpStatement): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: LabelNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: LiteralNode[_]): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: LoopNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: NameSpaceImportNode): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: NamedImportsNode): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: ObjectNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: PropertyNode): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: ReturnNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: RuntimeNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: Statement): T = {
    visit(node.asInstanceOf[Node])
  }

  def visit(node: SwitchNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: TernaryNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: ThrowNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: TryNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: UnaryNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: JoinPredecessorExpression): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: VarNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: WhileNode): T = {
    visit(node.asInstanceOf[LoopNode])
  }

  def visit(node: WithNode): T = {
    visit(node.asInstanceOf[Statement])
  }

  def visit(node: ClassNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: BlockExpression): T = {
    visit(node.asInstanceOf[Expression])
  }

  def visit(node: ParameterNode): T = {
    visit(node.asInstanceOf[Expression])
  }

  override def enterDefault(node: Node): T = {
    visit(node)
  }

  override def enterAccessNode(node: AccessNode): T = {
    visit(node)
  }

  override def enterBlock(node: Block): T = {
    visit(node)
  }

  override def enterBinaryNode(node: BinaryNode): T = {
    visit(node)
  }

  override def enterBreakNode(node: BreakNode): T = {
    visit(node)
  }

  override def enterCallNode(node: CallNode): T = {
    visit(node)
  }

  override def enterCaseNode(node: CaseNode): T = {
    visit(node)
  }

  override def enterCatchNode(node: CatchNode): T = {
    visit(node)
  }

  override def enterContinueNode(node: ContinueNode): T = {
    visit(node)
  }

  override def enterDebuggerNode(node: DebuggerNode): T = {
    visit(node)
  }

  override def enterEmptyNode(node: EmptyNode): T = {
    visit(node)
  }

  override def enterErrorNode(node: ErrorNode): T = {
    visit(node)
  }

  override def enterExportNode(node: ExportNode): T = {
    visit(node)
  }

  override def enterExportSpecifierNode(node: ExportSpecifierNode): T = {
    visit(node)
  }

  override def enterExpressionStatement(node: ExpressionStatement): T = {
    visit(node)
  }

  override def enterBlockStatement(node: BlockStatement): T = {
    visit(node)
  }

  override def enterForNode(node: ForNode): T = {
    visit(node)
  }

  override def enterFromNode(node: FromNode): T = {
    visit(node)
  }

  override def enterFunctionNode(node: FunctionNode): T = {
    visit(node)
  }

  override def enterIdentNode(node: IdentNode): T = {
    visit(node)
  }

  override def enterIfNode(node: IfNode): T = {
    visit(node)
  }

  override def enterImportClauseNode(node: ImportClauseNode): T = {
    visit(node)
  }

  override def enterImportNode(node: ImportNode): T = {
    visit(node)
  }

  override def enterImportSpecifierNode(node: ImportSpecifierNode): T = {
    visit(node)
  }

  override def enterIndexNode(node: IndexNode): T = {
    visit(node)
  }

  override def enterLabelNode(node: LabelNode): T = {
    visit(node)
  }

  override def enterLiteralNode(node: LiteralNode[_]): T = {
    visit(node)
  }

  override def enterNameSpaceImportNode(node: NameSpaceImportNode): T = {
    visit(node)
  }

  override def enterNamedImportsNode(node: NamedImportsNode): T = {
    visit(node)
  }

  override def enterObjectNode(node: ObjectNode): T = {
    visit(node)
  }

  override def enterPropertyNode(node: PropertyNode): T = {
    visit(node)
  }

  override def enterReturnNode(node: ReturnNode): T = {
    visit(node)
  }

  override def enterRuntimeNode(node: RuntimeNode): T = {
    visit(node)
  }

  override def enterSwitchNode(node: SwitchNode): T = {
    visit(node)
  }

  override def enterTernaryNode(node: TernaryNode): T = {
    visit(node)
  }

  override def enterThrowNode(node: ThrowNode): T = {
    visit(node)
  }

  override def enterTryNode(node: TryNode): T = {
    visit(node)
  }

  override def enterUnaryNode(node: UnaryNode): T = {
    visit(node)
  }

  override def enterJoinPredecessorExpression(node: JoinPredecessorExpression): T = {
    visit(node)
  }

  override def enterVarNode(node: VarNode): T = {
    visit(node)
  }

  override def enterWhileNode(node: WhileNode): T = {
    visit(node)
  }

  override def enterWithNode(node: WithNode): T = {
    visit(node)
  }

  override def enterClassNode(node: ClassNode): T = {
    visit(node)
  }

  override def enterBlockExpression(node: BlockExpression): T = {
    visit(node)
  }

  override def enterParameterNode(node: ParameterNode): T = {
    visit(node)
  }

  override def enterNamedExportsNode(node: NamedExportsNode): T = {
    visit(node)
  }
}

package io.shiftleft.js2cpg.parser

import com.oracle.js.parser.ir
import com.oracle.js.parser.ir.*
import com.oracle.js.parser.ir.visitor.NodeVisitor

// just a convenience class to make all the methods at hand explicit
abstract class DefaultAstVisitor(lexicalContext: LexicalContext = new LexicalContext())
    extends NodeVisitor[LexicalContext](lexicalContext) {

  override def enterDefault(node: Node): Boolean = {
    super.enterDefault(node)
  }

  override def leaveDefault(node: Node): Node = {
    super.leaveDefault(node)
  }

  override def enterAccessNode(accessNode: AccessNode): Boolean =
    super.enterAccessNode(accessNode)

  override def leaveAccessNode(accessNode: AccessNode): Node =
    super.leaveAccessNode(accessNode)

  override def enterBlock(block: ir.Block): Boolean = super.enterBlock(block)

  override def leaveBlock(block: ir.Block): Node = super.leaveBlock(block)

  override def enterBinaryNode(binaryNode: BinaryNode): Boolean =
    super.enterBinaryNode(binaryNode)

  override def leaveBinaryNode(binaryNode: BinaryNode): Node =
    super.leaveBinaryNode(binaryNode)

  override def enterBreakNode(breakNode: BreakNode): Boolean =
    super.enterBreakNode(breakNode)

  override def leaveBreakNode(breakNode: BreakNode): Node =
    super.leaveBreakNode(breakNode)

  override def enterCallNode(callNode: CallNode): Boolean =
    super.enterCallNode(callNode)

  override def leaveCallNode(callNode: CallNode): Node =
    super.leaveCallNode(callNode)

  override def enterCaseNode(caseNode: CaseNode): Boolean =
    super.enterCaseNode(caseNode)

  override def leaveCaseNode(caseNode: CaseNode): Node =
    super.leaveCaseNode(caseNode)

  override def enterCatchNode(catchNode: CatchNode): Boolean =
    super.enterCatchNode(catchNode)

  override def leaveCatchNode(catchNode: CatchNode): Node =
    super.leaveCatchNode(catchNode)

  override def enterContinueNode(continueNode: ContinueNode): Boolean =
    super.enterContinueNode(continueNode)

  override def leaveContinueNode(continueNode: ContinueNode): Node =
    super.leaveContinueNode(continueNode)

  override def enterDebuggerNode(debuggerNode: DebuggerNode): Boolean =
    super.enterDebuggerNode(debuggerNode)

  override def leaveDebuggerNode(debuggerNode: DebuggerNode): Node =
    super.leaveDebuggerNode(debuggerNode)

  override def enterEmptyNode(emptyNode: EmptyNode): Boolean =
    super.enterEmptyNode(emptyNode)

  override def leaveEmptyNode(emptyNode: EmptyNode): Node =
    super.leaveEmptyNode(emptyNode)

  override def enterErrorNode(errorNode: ir.ErrorNode): Boolean =
    super.enterErrorNode(errorNode)

  override def leaveErrorNode(errorNode: ir.ErrorNode): Node =
    super.leaveErrorNode(errorNode)

  override def enterNamedExportsNode(namedExportsNode: NamedExportsNode): Boolean =
    super.enterNamedExportsNode(namedExportsNode)

  override def leaveNamedExportsNode(namedExportsNode: NamedExportsNode): Node =
    super.leaveNamedExportsNode(namedExportsNode)

  override def enterExportNode(exportNode: ExportNode): Boolean =
    super.enterExportNode(exportNode)

  override def leaveExportNode(exportNode: ExportNode): Node =
    super.leaveExportNode(exportNode)

  override def enterExportSpecifierNode(exportSpecifierNode: ExportSpecifierNode): Boolean =
    super.enterExportSpecifierNode(exportSpecifierNode)

  override def leaveExportSpecifierNode(exportSpecifierNode: ExportSpecifierNode): Node =
    super.leaveExportSpecifierNode(exportSpecifierNode)

  override def enterExpressionStatement(expressionStatement: ir.ExpressionStatement): Boolean =
    super.enterExpressionStatement(expressionStatement)

  override def leaveExpressionStatement(expressionStatement: ir.ExpressionStatement): Node =
    super.leaveExpressionStatement(expressionStatement)

  override def enterBlockStatement(blockStatement: BlockStatement): Boolean =
    super.enterBlockStatement(blockStatement)

  override def leaveBlockStatement(blockStatement: BlockStatement): Node =
    super.leaveBlockStatement(blockStatement)

  override def enterForNode(forNode: ForNode): Boolean =
    super.enterForNode(forNode)

  override def leaveForNode(forNode: ForNode): Node =
    super.leaveForNode(forNode)

  override def enterFromNode(fromNode: FromNode): Boolean =
    super.enterFromNode(fromNode)

  override def leaveFromNode(fromNode: FromNode): Node =
    super.leaveFromNode(fromNode)

  override def enterFunctionNode(functionNode: ir.FunctionNode): Boolean =
    super.enterFunctionNode(functionNode)

  override def leaveFunctionNode(functionNode: ir.FunctionNode): Node =
    super.leaveFunctionNode(functionNode)

  override def enterIdentNode(identNode: IdentNode): Boolean =
    super.enterIdentNode(identNode)

  override def leaveIdentNode(identNode: IdentNode): Node =
    super.leaveIdentNode(identNode)

  override def enterIfNode(ifNode: IfNode): Boolean = super.enterIfNode(ifNode)

  override def leaveIfNode(ifNode: IfNode): Node = super.leaveIfNode(ifNode)

  override def enterImportClauseNode(importClauseNode: ImportClauseNode): Boolean =
    super.enterImportClauseNode(importClauseNode)

  override def leaveImportClauseNode(importClauseNode: ImportClauseNode): Node =
    super.leaveImportClauseNode(importClauseNode)

  override def enterImportNode(importNode: ImportNode): Boolean =
    super.enterImportNode(importNode)

  override def leaveImportNode(importNode: ImportNode): Node =
    super.leaveImportNode(importNode)

  override def enterImportSpecifierNode(importSpecifierNode: ImportSpecifierNode): Boolean =
    super.enterImportSpecifierNode(importSpecifierNode)

  override def leaveImportSpecifierNode(importSpecifierNode: ImportSpecifierNode): Node =
    super.leaveImportSpecifierNode(importSpecifierNode)

  override def enterIndexNode(indexNode: IndexNode): Boolean =
    super.enterIndexNode(indexNode)

  override def leaveIndexNode(indexNode: IndexNode): Node =
    super.leaveIndexNode(indexNode)

  override def enterLabelNode(labelNode: LabelNode): Boolean =
    super.enterLabelNode(labelNode)

  override def leaveLabelNode(labelNode: LabelNode): Node =
    super.leaveLabelNode(labelNode)

  override def enterLiteralNode(literalNode: LiteralNode[?]): Boolean =
    super.enterLiteralNode(literalNode)

  override def leaveLiteralNode(literalNode: LiteralNode[?]): Node =
    super.leaveLiteralNode(literalNode)

  override def enterNameSpaceImportNode(nameSpaceImportNode: NameSpaceImportNode): Boolean =
    super.enterNameSpaceImportNode(nameSpaceImportNode)

  override def leaveNameSpaceImportNode(nameSpaceImportNode: NameSpaceImportNode): Node =
    super.leaveNameSpaceImportNode(nameSpaceImportNode)

  override def enterNamedImportsNode(namedImportsNode: NamedImportsNode): Boolean =
    super.enterNamedImportsNode(namedImportsNode)

  override def leaveNamedImportsNode(namedImportsNode: NamedImportsNode): Node =
    super.leaveNamedImportsNode(namedImportsNode)

  override def enterObjectNode(objectNode: ObjectNode): Boolean =
    super.enterObjectNode(objectNode)

  override def leaveObjectNode(objectNode: ObjectNode): Node =
    super.leaveObjectNode(objectNode)

  override def enterPropertyNode(propertyNode: PropertyNode): Boolean =
    super.enterPropertyNode(propertyNode)

  override def leavePropertyNode(propertyNode: PropertyNode): Node =
    super.leavePropertyNode(propertyNode)

  override def enterReturnNode(returnNode: ReturnNode): Boolean =
    super.enterReturnNode(returnNode)

  override def leaveReturnNode(returnNode: ReturnNode): Node =
    super.leaveReturnNode(returnNode)

  override def enterTemplateLiteralNode(templateLiteralNode: TemplateLiteralNode): Boolean =
    super.enterTemplateLiteralNode(templateLiteralNode)

  override def leaveTemplateLiteralNode(templateLiteralNode: TemplateLiteralNode): Node =
    super.leaveTemplateLiteralNode(templateLiteralNode)

  override def enterSwitchNode(switchNode: SwitchNode): Boolean =
    super.enterSwitchNode(switchNode)

  override def leaveSwitchNode(switchNode: SwitchNode): Node =
    super.leaveSwitchNode(switchNode)

  override def enterTernaryNode(ternaryNode: TernaryNode): Boolean =
    super.enterTernaryNode(ternaryNode)

  override def leaveTernaryNode(ternaryNode: TernaryNode): Node =
    super.leaveTernaryNode(ternaryNode)

  override def enterThrowNode(throwNode: ThrowNode): Boolean =
    super.enterThrowNode(throwNode)

  override def leaveThrowNode(throwNode: ThrowNode): Node =
    super.leaveThrowNode(throwNode)

  override def enterTryNode(tryNode: TryNode): Boolean =
    super.enterTryNode(tryNode)

  override def leaveTryNode(tryNode: TryNode): Node =
    super.leaveTryNode(tryNode)

  override def enterUnaryNode(unaryNode: UnaryNode): Boolean =
    super.enterUnaryNode(unaryNode)

  override def leaveUnaryNode(unaryNode: UnaryNode): Node =
    super.leaveUnaryNode(unaryNode)

  override def enterJoinPredecessorExpression(expr: JoinPredecessorExpression): Boolean =
    super.enterJoinPredecessorExpression(expr)

  override def leaveJoinPredecessorExpression(expr: JoinPredecessorExpression): Node =
    super.leaveJoinPredecessorExpression(expr)

  override def enterVarNode(varNode: VarNode): Boolean =
    super.enterVarNode(varNode)

  override def leaveVarNode(varNode: VarNode): Node =
    super.leaveVarNode(varNode)

  override def enterWhileNode(whileNode: WhileNode): Boolean =
    super.enterWhileNode(whileNode)

  override def leaveWhileNode(whileNode: WhileNode): Node =
    super.leaveWhileNode(whileNode)

  override def enterWithNode(withNode: WithNode): Boolean =
    super.enterWithNode(withNode)

  override def leaveWithNode(withNode: WithNode): Node =
    super.leaveWithNode(withNode)

  override def enterClassNode(classNode: ClassNode): Boolean =
    super.enterClassNode(classNode)

  override def leaveClassNode(classNode: ClassNode): Node =
    super.leaveClassNode(classNode)

  override def enterBlockExpression(blockExpression: BlockExpression): Boolean =
    super.enterBlockExpression(blockExpression)

  override def leaveBlockExpression(blockExpression: BlockExpression): Node =
    super.leaveBlockExpression(blockExpression)

  override def enterParameterNode(paramNode: ParameterNode): Boolean =
    super.enterParameterNode(paramNode)

  override def leaveParameterNode(paramNode: ParameterNode): Node =
    super.leaveParameterNode(paramNode)
}

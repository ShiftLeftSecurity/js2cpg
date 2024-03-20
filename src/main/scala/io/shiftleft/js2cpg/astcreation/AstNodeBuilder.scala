package io.shiftleft.js2cpg.astcreation

import com.oracle.js.parser.ir._
import io.shiftleft.codepropertygraph.generated.nodes._
import io.shiftleft.codepropertygraph.generated.{DispatchTypes, EvaluationStrategies, Operators}
import io.shiftleft.js2cpg.datastructures.{LineAndColumn, OrderTracker}
import io.shiftleft.js2cpg.datastructures.scope.{MethodScope, Scope}
import io.shiftleft.js2cpg.passes.Defines
import io.shiftleft.js2cpg.parser.JsSource
import io.shiftleft.js2cpg.parser.JsSource.shortenCode
import overflowdb.BatchedUpdate.DiffGraphBuilder

class AstNodeBuilder(
  private val diffGraph: DiffGraphBuilder,
  private val astEdgeBuilder: AstEdgeBuilder,
  private val astCreator: AstCreator,
  private val source: JsSource,
  private val scope: Scope
) {

  implicit def int2IntegerOpt(x: Option[Int]): Option[Integer] = x.map(java.lang.Integer.valueOf)
  implicit def int2Integer(x: Int): Integer                    = java.lang.Integer.valueOf(x)

  def codeOf(node: NewNode): String = node match {
    case node: AstNodeNew => node.code
    case _                => ""
  }

  def lineAndColumn(node: Node): LineAndColumn = {
    LineAndColumn(source.getLine(node), source.getColumn(node))
  }

  def createDependencyNode(name: String, groupId: String, version: String): NewDependency = {
    val dependency = NewDependency()
      .name(Option(name).getOrElse("<n/a>"))
      .dependencyGroupId(Option(groupId).getOrElse("<n/a>"))
      .version(Option(version).getOrElse("<n/a>"))
    diffGraph.addNode(dependency)
    dependency
  }

  def groupIdFromImportNode(importNode: ImportNode): String = {
    importNode.getFrom match {
      case null => importNode.getModuleSpecifier.getString
      case from => from.getModuleSpecifier.getString
    }
  }

  def createParameterInNode(
    name: String,
    code: String,
    methodNode: NewMethod,
    lineAndColumnProvider: Node,
    orderTracker: OrderTracker
  ): NewMethodParameterIn = {
    val lineColumn = lineAndColumn(lineAndColumnProvider)
    val line       = lineColumn.line
    val column     = lineColumn.column
    val param = NewMethodParameterIn()
      .name(name)
      .code(shortenCode(code))
      .evaluationStrategy(EvaluationStrategies.BY_VALUE)
      .lineNumber(line)
      .columnNumber(column)
      .order(orderTracker.order)
      .typeFullName(Defines.Any)

    diffGraph.addNode(param)
    orderTracker.inc()
    astEdgeBuilder.addAstEdge(param, methodNode)
    scope.addVariable(name, param, MethodScope)
    param
  }

  def createImportNode(importNode: ImportNode): NewImport = {
    val lineColumn = lineAndColumn(importNode)
    val line       = lineColumn.line
    val column     = lineColumn.column

    val importedEntity = groupIdFromImportNode(importNode) match {
      case "" => None
      case x  => Some(x)
    }

    val node = NewImport()
      .importedEntity(importedEntity)
      .code(importNode.toString().stripSuffix(";"))
      .lineNumber(line)
      .columnNumber(column)

    diffGraph.addNode(node)
    node
  }

  private def sanitizeCode(node: Node): String = node match {
    case _: ReturnNode =>
      source.getCode(node).stripSuffix(";")
    case _: BreakNode =>
      source.getCode(node).stripSuffix(";")
    case _: ContinueNode =>
      source.getCode(node).stripSuffix(";")
    case _: ErrorNode =>
      // ErrorNode represents a runtime call; does not have a code representation
      "<error>"
    case _ =>
      source.getCode(node)
  }

  def createUnknownNode(parserNode: Node): NewUnknown = {
    val code       = sanitizeCode(parserNode)
    val lineColumn = lineAndColumn(parserNode)
    val unknown = NewUnknown()
      .parserTypeName(parserNode.getClass.getSimpleName)
      .lineNumber(lineColumn.line)
      .columnNumber(lineColumn.column)
      .code(shortenCode(code))
      .typeFullName(Defines.Any)

    diffGraph.addNode(unknown)
    unknown
  }

  def createTypeDeclNode(
    node: Node,
    name: String,
    fullName: String,
    astParentType: String,
    astParentFullName: String,
    inheritsFrom: Option[String]
  ): NewTypeDecl = {
    val typeDecl = NewTypeDecl()
      .name(name)
      .fullName(fullName)
      .astParentType(astParentType)
      .astParentFullName(astParentFullName)
      .isExternal(false)
      .inheritsFromTypeFullName(inheritsFrom.toList)
      .filename(source.filePath)
    astCreator.offsets(node).foreach { (start, end) =>
      typeDecl.offset(start).offsetEnd(end)
    }
    diffGraph.addNode(typeDecl)
    typeDecl
  }

  def createIdentifierNode(
    name: String,
    lineAndColumnProvider: Node,
    dynamicTypeOption: Option[String]
  ): NewIdentifier = {
    val lineColumn = lineAndColumn(lineAndColumnProvider)
    val line       = lineColumn.line
    val column     = lineColumn.column

    val identifier = NewIdentifier()
      .name(name)
      .code(shortenCode(name))
      .lineNumber(line)
      .columnNumber(column)
      .typeFullName(Defines.Any)
      .dynamicTypeHintFullName(dynamicTypeOption.toList)
    diffGraph.addNode(identifier)
    identifier
  }

  def createFieldIdentifierNode(name: String, lineAndColumnProvider: Node): NewFieldIdentifier = {
    val lineColumn = lineAndColumn(lineAndColumnProvider)
    val line       = lineColumn.line
    val column     = lineColumn.column

    val fieldIdentifier = NewFieldIdentifier()
      .code(shortenCode(name))
      .canonicalName(name)
      .lineNumber(line)
      .columnNumber(column)
    diffGraph.addNode(fieldIdentifier)
    fieldIdentifier
  }

  def createFieldAccessNode(baseId: NewNode, partId: NewNode, lineAndColumn: LineAndColumn): NewCall = {
    val call = createCallNode(
      codeOf(baseId) + "." + codeOf(partId),
      Operators.fieldAccess,
      DispatchTypes.STATIC_DISPATCH,
      lineAndColumn
    )

    astEdgeBuilder.addAstEdge(baseId, call, 1)
    astEdgeBuilder.addArgumentEdge(baseId, call, 1)

    astEdgeBuilder.addAstEdge(partId, call, 2)
    astEdgeBuilder.addArgumentEdge(partId, call, 2)

    call
  }

  def createStaticCallNode(
    code: String,
    methodName: String,
    fullName: String,
    lineAndColumn: LineAndColumn
  ): NewCall = {
    val line   = lineAndColumn.line
    val column = lineAndColumn.column
    val call = NewCall()
      .code(shortenCode(code))
      .name(methodName)
      .methodFullName(fullName)
      .dispatchType(DispatchTypes.STATIC_DISPATCH)
      .signature("")
      .lineNumber(line)
      .columnNumber(column)
      .typeFullName(Defines.Any)

    diffGraph.addNode(call)
    call
  }

  def createEqualsCallNode(lhsId: NewNode, rhsId: NewNode, lineAndColumn: LineAndColumn): NewCall = {
    val call = createCallNode(
      codeOf(lhsId) + " === " + codeOf(rhsId),
      Operators.equals,
      DispatchTypes.STATIC_DISPATCH,
      lineAndColumn
    )

    astEdgeBuilder.addAstEdge(lhsId, call, 1)
    astEdgeBuilder.addArgumentEdge(lhsId, call, 1)

    astEdgeBuilder.addAstEdge(rhsId, call, 2)
    astEdgeBuilder.addArgumentEdge(rhsId, call, 2)

    call
  }

  def createIndexAccessNode(baseId: NewNode, indexId: NewNode, lineAndColumn: LineAndColumn): NewCall = {
    val call = createCallNode(
      codeOf(baseId) + "[" + codeOf(indexId) + "]",
      Operators.indexAccess,
      DispatchTypes.STATIC_DISPATCH,
      lineAndColumn
    )

    astEdgeBuilder.addAstEdge(baseId, call, 1)
    astEdgeBuilder.addArgumentEdge(baseId, call, 1)

    astEdgeBuilder.addAstEdge(indexId, call, 2)
    astEdgeBuilder.addArgumentEdge(indexId, call, 2)
    call
  }

  def createAssignmentNode(
    destId: NewNode,
    sourceId: NewNode,
    lineAndColumn: LineAndColumn,
    withParenthesis: Boolean = false,
    customCode: String = ""
  ): NewCall = {
    val code = if (customCode.isEmpty) {
      if (withParenthesis) {
        s"(${codeOf(destId)} = ${codeOf(sourceId)})"
      } else {
        s"${codeOf(destId)} = ${codeOf(sourceId)}"
      }
    } else {
      customCode
    }

    val call =
      createCallNode(code, Operators.assignment, DispatchTypes.STATIC_DISPATCH, lineAndColumn)

    astEdgeBuilder.addAstEdge(destId, call, 1)
    astEdgeBuilder.addArgumentEdge(destId, call, 1)

    astEdgeBuilder.addAstEdge(sourceId, call, 2)
    astEdgeBuilder.addArgumentEdge(sourceId, call, 2)

    call
  }

  def createLiteralNode(code: String, lineAndColumn: LineAndColumn, dynamicTypeOption: Option[String]): NewLiteral = {
    val line   = lineAndColumn.line
    val column = lineAndColumn.column
    val literal = NewLiteral()
      .code(shortenCode(code))
      .typeFullName(Defines.Any)
      .lineNumber(line)
      .columnNumber(column)
      .dynamicTypeHintFullName(dynamicTypeOption.toList)
    diffGraph.addNode(literal)
    literal
  }

  // TODO
  // This method is a hack which does not handle complex property keys.
  // It only creates a FIELD_IDENTIFIER node in order to not break the backend
  // assumption of only having FIELD_IDENTIFIER as second argument to
  // <operator>.fieldAccess calls.
  def createPropertyKeyNode(propertyNode: PropertyNode): NewFieldIdentifier = {
    propertyNode.getKey match {
      case identNode: IdentNode =>
        createFieldIdentifierNode(identNode.getName, propertyNode.getKey)
      case literalNode: LiteralNode[_] =>
        createFieldIdentifierNode(literalNode.getValue.toString, propertyNode.getKey)
      case _ =>
        // TODO: handle other kinds of possible nodes (e.g., computed property name)
        createFieldIdentifierNode(source.getCode(propertyNode.getKey), propertyNode.getKey)
    }
  }

  def createTernaryNode(testId: NewNode, trueId: NewNode, falseId: NewNode, lineAndColumn: LineAndColumn): NewCall = {
    val code = codeOf(testId) + " ? " + codeOf(trueId) + " : " + codeOf(falseId)
    val callId =
      createCallNode(code, Operators.conditional, DispatchTypes.STATIC_DISPATCH, lineAndColumn)

    astEdgeBuilder.addAstEdge(testId, callId, 1)
    astEdgeBuilder.addArgumentEdge(testId, callId, 1)
    astEdgeBuilder.addAstEdge(trueId, callId, 2)
    astEdgeBuilder.addArgumentEdge(trueId, callId, 2)
    astEdgeBuilder.addAstEdge(falseId, callId, 3)
    astEdgeBuilder.addArgumentEdge(falseId, callId, 3)

    callId
  }

  def createCallNode(code: String, callName: String, dispatchType: String, lineAndColumn: LineAndColumn): NewCall = {
    val line   = lineAndColumn.line
    val column = lineAndColumn.column
    val call = NewCall()
      .code(shortenCode(code))
      .name(callName)
      .methodFullName(callName)
      .dispatchType(dispatchType)
      .lineNumber(line)
      .columnNumber(column)
      .typeFullName(Defines.Any)

    diffGraph.addNode(call)
    call
  }

  def createFileNode(fileName: String): NewFile = {
    val fileNode = NewFile()
      .name(fileName)
    diffGraph.addNode(fileNode)
    fileNode
  }

  def createNamespaceBlockNode(fullName: String): NewNamespaceBlock = {
    val namespaceBlock = NewNamespaceBlock()
      .name(Defines.GlobalNamespace)
      .fullName(fullName)
      .filename(source.filePath)
      .order(1)
    diffGraph.addNode(namespaceBlock)
    namespaceBlock
  }

  def createClosureBindingNode(closureBindingId: String, closureOriginalName: String): NewClosureBinding = {
    val closureBinding = NewClosureBinding()
      .closureBindingId(Some(closureBindingId))
      .evaluationStrategy(EvaluationStrategies.BY_REFERENCE)
      .closureOriginalName(Some(closureOriginalName))

    diffGraph.addNode(closureBinding)
    closureBinding
  }

  def createMethodRefNode(code: String, methodFullName: String, functionNode: FunctionNode): NewMethodRef = {
    val lineColumn = lineAndColumn(functionNode)
    val line       = lineColumn.line
    val column     = lineColumn.column
    val methodRef = NewMethodRef()
      .code(shortenCode(code))
      .methodFullName(methodFullName)
      .typeFullName(methodFullName)
      .lineNumber(line)
      .columnNumber(column)
    diffGraph.addNode(methodRef)
    methodRef
  }

  def createTypeRefNode(code: String, typeFullName: String, classNode: ClassNode): NewTypeRef = {
    val lineColumn = lineAndColumn(classNode)
    val line       = lineColumn.line
    val column     = lineColumn.column
    val typeRef = NewTypeRef()
      .code(shortenCode(code))
      .typeFullName(typeFullName)
      .lineNumber(line)
      .columnNumber(column)
    diffGraph.addNode(typeRef)
    typeRef
  }

  def createMethodNode(methodName: String, methodFullName: String, functionNode: FunctionNode): NewMethod = {
    val lineColumn = lineAndColumn(functionNode)
    val line       = lineColumn.line
    val column     = lineColumn.column
    val code       = shortenCode(sanitizeCode(functionNode))

    val method = NewMethod()
      .name(methodName)
      .filename(source.filePath)
      .code(code)
      .fullName(methodFullName)
      .isExternal(false)
      .lineNumber(line)
      .columnNumber(column)
    astCreator.offsets(functionNode).foreach { (start, end) =>
      method.offset(start).offsetEnd(end)
    }
    diffGraph.addNode(method)
    method
  }

  def createModifierNode(modifierType: String): NewModifier = {
    val modifier = NewModifier()
      .modifierType(modifierType)
    diffGraph.addNode(modifier)
    modifier
  }

  def createBlockNode(node: Node, keepWholeCode: Boolean = false, customCode: Option[String] = None): NewBlock = {
    val lineColumn = lineAndColumn(node)
    val line       = lineColumn.line
    val column     = lineColumn.column
    val code = if (keepWholeCode) {
      customCode.getOrElse(sanitizeCode(node))
    } else {
      shortenCode(customCode.getOrElse(sanitizeCode(node)))
    }
    val block = NewBlock()
      .typeFullName(Defines.Any)
      .code(code)
      .lineNumber(line)
      .columnNumber(column)
    diffGraph.addNode(block)
    block
  }

  def createMethodReturnNode(lineAndColumn: LineAndColumn): NewMethodReturn = {
    val line   = lineAndColumn.line
    val column = lineAndColumn.column
    val code   = "RET"

    val ret = NewMethodReturn()
      .code(shortenCode(code))
      .evaluationStrategy(EvaluationStrategies.BY_VALUE)
      .typeFullName(Defines.Any)
      .lineNumber(line)
      .columnNumber(column)
    diffGraph.addNode(ret)
    ret
  }

  def createTypeNode(name: String, fullName: String): NewType = {
    val typ = NewType()
      .name(name)
      .fullName(fullName)
      .typeDeclFullName(fullName)
    diffGraph.addNode(typ)
    typ
  }

  def createBindingNode(): NewBinding = {
    val binding = NewBinding()
      .name("")
      .signature("")
    diffGraph.addNode(binding)
    binding
  }

  def createJumpTarget(caseNode: CaseNode): NewJumpTarget = {
    val jumpTarget = NewJumpTarget()
      .parserTypeName(caseNode.getClass.getSimpleName)
      .name(if (caseNode.toString().startsWith("case")) "case" else "default")
      .code(shortenCode(caseNode.toString()))
    diffGraph.addNode(jumpTarget)
    jumpTarget
  }

  def createControlStructureNode(node: Node, controlStructureType: String): NewControlStructure = {
    val controlStructure = NewControlStructure()
      .controlStructureType(controlStructureType)
      .code(shortenCode(source.getString(node)))
    diffGraph.addNode(controlStructure)
    controlStructure
  }

  def createMemberNode(name: String, node: Node, dynamicTypeOption: Option[String]): NewMember = {
    val member = NewMember()
      .code(shortenCode(source.getString(node)))
      .name(name)
      .typeFullName(Defines.Any)
      .dynamicTypeHintFullName(dynamicTypeOption.toList)
    diffGraph.addNode(member)
    member
  }

  def createLocalNode(name: String, typeFullName: String, closureBindingId: Option[String] = None): NewLocal = {
    val code = "N/A"
    val local = NewLocal()
      .code(shortenCode(code))
      .name(name)
      .typeFullName(typeFullName)
      .closureBindingId(closureBindingId)
    diffGraph.addNode(local)
    local
  }

  def createReturnNode(node: Node): NewReturn = {
    val lineColumn = lineAndColumn(node)
    val line       = lineColumn.line
    val column     = lineColumn.column
    val code       = sanitizeCode(node)
    val ret = NewReturn()
      .code(shortenCode(code))
      .lineNumber(line)
      .columnNumber(column)
    diffGraph.addNode(ret)
    ret
  }

}

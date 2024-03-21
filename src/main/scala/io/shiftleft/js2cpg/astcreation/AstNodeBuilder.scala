package io.shiftleft.js2cpg.astcreation

import com.oracle.js.parser.ir.*
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.codepropertygraph.generated.{DispatchTypes, EvaluationStrategies, Operators}
import io.shiftleft.js2cpg.datastructures.OrderTracker
import io.shiftleft.js2cpg.datastructures.scope.MethodScope
import io.shiftleft.js2cpg.passes.Defines
import io.shiftleft.js2cpg.parser.JsSource
import io.joern.x2cpg.utils.NodeBuilders
import io.shiftleft.js2cpg.parser.JsSource.SourceMapOrigin
import org.apache.commons.lang3.StringUtils

trait AstNodeBuilder extends io.joern.x2cpg.AstNodeBuilder[Node, AstNodeBuilder] { this: AstCreator =>

  private val MinCodeLength: Int        = 50
  private val DefaultMaxCodeLength: Int = 1000

  def codeOf(node: NewNode): String = node match {
    case node: AstNodeNew => node.code
    case _                => ""
  }

  override protected def line(node: Node): Option[Integer] =
    source.lineFromSourceMap(node).map(java.lang.Integer.valueOf)

  override protected def lineEnd(node: Node): Option[Integer] = None // impossible with transpilation / source maps

  override protected def column(node: Node): Option[Integer] =
    source.columnFromSourceMap(node).map(java.lang.Integer.valueOf)

  override protected def columnEnd(node: Node): Option[Integer] = None // impossible with transpilation / source maps

  /** @return
    *   the code of a node in the parsed file. If this file is the result of transpilation the original code is
    *   calculated from the corresponding sourcemap. Note: in this case, only the re-mapped starting line/column number
    *   are available. Hence, we extract only a fixed number of characters (max. until the end of the file).
    */
  override protected def code(node: Node): String = codeFromSourceMap(node)

  private def codeFromSourceMap(node: Node): String = {
    source.getSourceMap match {
      case Some(SourceMapOrigin(_, Some(sourceMap), sourceWithLineNumbers)) =>
        val line   = source.getLineOfSource(node.getStart) - 1
        val column = source.getColumnOfSource(node.getStart)
        sourceMap.getMapping(line, column) match {
          case null =>
            source.source.getString(node.getStart, node.getFinish - node.getStart)
          case mapping =>
            val originLine   = mapping.getSourceLine
            val originColumn = mapping.getSourceColumn
            val transpiledCodeLength = node.getFinish - node.getStart match {
              // for some transpiled nodes the start and finish indices are wrong:
              case 0     => node.toString.length
              case other => other
            }
            sourceWithLineNumbers.get(originLine) match {
              case Some(startingCodeLine) =>
                // Code from the origin source file was found.
                val maxCodeLength = math.min(transpiledCodeLength, DefaultMaxCodeLength)
                // We are extra careful: we do not want to generate empty lines.
                // That can happen e.g., for synthetic return statements.
                // Hence, we back up 1 char.
                val startingCode =
                  startingCodeLine.substring(math.min(math.max(startingCodeLine.length - 1, 0), originColumn))
                calculateCode(sourceWithLineNumbers, startingCode, originLine, maxCodeLength)
              case None =>
                // It has an actual mapping, but it is synthetic code not found in the source file.
                // We return the synthetic code.
                source.source.getString(node.getStart, node.getFinish - node.getStart)
            }
        }
      case _ =>
        // No mapping at all. We return the node code.
        source.source.getString(node.getStart, node.getFinish - node.getStart)
    }
  }

  /** Code field calculation:
    *   - We start with the re-mapped line/column number.
    *   - We always read at the length of the transpiled node (except if the original file ends earlier) capped at
    *     MAX_CODE_LENGTH.
    *   - If there would be more content we append ' [...]'.
    */
  @scala.annotation.tailrec
  private def calculateCode(
    sourceWithLineNumbers: Map[Int, String],
    currentLine: String,
    currentLineNumber: Int,
    transpiledCodeLength: Int
  ): String = {
    currentLine match {
      case line if line.length >= transpiledCodeLength =>
        StringUtils.abbreviate(line, math.max(MinCodeLength, transpiledCodeLength - 1))
      case line if line.length < transpiledCodeLength && sourceWithLineNumbers.contains(currentLineNumber + 1) =>
        calculateCode(
          sourceWithLineNumbers,
          line + "\n" + sourceWithLineNumbers(currentLineNumber + 1),
          currentLineNumber + 1,
          transpiledCodeLength
        )
      case line =>
        line.stripLineEnd
    }
  }

  def createDependencyNode(name: String, groupId: String, version: String): NewDependency = {
    val dependency = NodeBuilders.newDependencyNode(
      Option(name).getOrElse("<n/a>"),
      Option(groupId).getOrElse("<n/a>"),
      Option(version).getOrElse("<n/a>")
    )
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
    val param = parameterInNode(
      lineAndColumnProvider,
      name,
      shortenCode(code),
      orderTracker.order,
      false,
      EvaluationStrategies.BY_VALUE,
      Defines.Any
    )
    diffGraph.addNode(param)
    orderTracker.inc()
    addAstEdge(param, methodNode)
    scope.addVariable(name, param, MethodScope)
    param
  }

  def createImportNode(importNode: ImportNode): NewImport = {
    val importedEntity = groupIdFromImportNode(importNode)
    val code_          = importNode.toString().stripSuffix(";")
    val node           = newImportNode(code_, importedEntity, "", importNode)
    diffGraph.addNode(node)
    node
  }

  private def sanitizeCode(node: Node): String = node match {
    case _: ReturnNode =>
      code(node).stripSuffix(";")
    case _: BreakNode =>
      code(node).stripSuffix(";")
    case _: ContinueNode =>
      code(node).stripSuffix(";")
    case _: ErrorNode =>
      // ErrorNode represents a runtime call; does not have a code representation
      "<error>"
    case _ =>
      code(node)
  }

  def createUnknownNode(parserNode: Node): NewUnknown = {
    val code    = sanitizeCode(parserNode)
    val unknown = unknownNode(parserNode, shortenCode(code))
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
    val typeDecl = typeDeclNode(
      node,
      name,
      fullName,
      source.filePath,
      code(node),
      astParentType,
      astParentFullName,
      inheritsFrom.toList
    )
    typeDecl
  }

  def createIdentifierNode(
    name: String,
    lineAndColumnProvider: Node,
    dynamicTypeOption: Option[String]
  ): NewIdentifier = {
    val identifier =
      identifierNode(lineAndColumnProvider, name, shortenCode(name), Defines.Any, dynamicTypeOption.toList)
    diffGraph.addNode(identifier)
    identifier
  }

  def createFieldIdentifierNode(name: String, lineAndColumnProvider: Node): NewFieldIdentifier = {
    val fieldIdentifier = fieldIdentifierNode(lineAndColumnProvider, name, shortenCode(name))
    diffGraph.addNode(fieldIdentifier)
    fieldIdentifier
  }

  def createFieldAccessCallNode(baseId: NewNode, partId: NewNode, node: Node): NewCall = {
    val code = codeOf(baseId) + "." + codeOf(partId)
    val call = callNode(node, code, Operators.fieldAccess, Operators.fieldAccess, DispatchTypes.STATIC_DISPATCH)
    addAstEdge(baseId, call, 1)
    addArgumentEdge(baseId, call, 1)
    addAstEdge(partId, call, 2)
    addArgumentEdge(partId, call, 2)
    call
  }

  def createStaticCallNode(code: String, methodName: String, fullName: String, node: Node): NewCall = {
    val call = callNode(
      node,
      shortenCode(code),
      methodName,
      fullName,
      DispatchTypes.STATIC_DISPATCH,
      signature = Some(""),
      typeFullName = Some(Defines.Any)
    )
    diffGraph.addNode(call)
    call
  }

  def createEqualsCallNode(lhsId: NewNode, rhsId: NewNode, node: Node): NewCall = {
    val call = callNode(
      node,
      codeOf(lhsId) + " === " + codeOf(rhsId),
      Operators.equals,
      Operators.equals,
      DispatchTypes.STATIC_DISPATCH
    )
    addAstEdge(lhsId, call, 1)
    addArgumentEdge(lhsId, call, 1)
    addAstEdge(rhsId, call, 2)
    addArgumentEdge(rhsId, call, 2)
    call
  }

  def createIndexAccessNode(baseId: NewNode, indexId: NewNode, node: Node): NewCall = {
    val call = callNode(
      node,
      codeOf(baseId) + "[" + codeOf(indexId) + "]",
      Operators.indexAccess,
      Operators.indexAccess,
      DispatchTypes.STATIC_DISPATCH
    )
    addAstEdge(baseId, call, 1)
    addArgumentEdge(baseId, call, 1)
    addAstEdge(indexId, call, 2)
    addArgumentEdge(indexId, call, 2)
    call
  }

  def createAssignmentNode(
    destId: NewNode,
    sourceId: NewNode,
    node: Node,
    withParenthesis: Boolean = false,
    customCode: String = ""
  ): NewCall = {
    val code = if (customCode.isEmpty) {
      if (withParenthesis) { s"(${codeOf(destId)} = ${codeOf(sourceId)})" }
      else { s"${codeOf(destId)} = ${codeOf(sourceId)}" }
    } else { customCode }
    val call = callNode(node, code, Operators.assignment, Operators.assignment, DispatchTypes.STATIC_DISPATCH)
    addAstEdge(destId, call, 1)
    addArgumentEdge(destId, call, 1)
    addAstEdge(sourceId, call, 2)
    addArgumentEdge(sourceId, call, 2)

    call
  }

  def createLiteralNode(code: String, node: Node, dynamicTypeOption: Option[String]): NewLiteral = {
    val literal = literalNode(node, shortenCode(code), Defines.Any, dynamicTypeOption.toList)
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
        createFieldIdentifierNode(code(propertyNode.getKey), propertyNode.getKey)
    }
  }

  def createTernaryNode(testId: NewNode, trueId: NewNode, falseId: NewNode, node: Node): NewCall = {
    val code = codeOf(testId) + " ? " + codeOf(trueId) + " : " + codeOf(falseId)
    val call = callNode(node, code, Operators.conditional, Operators.conditional, DispatchTypes.STATIC_DISPATCH)
    addAstEdge(testId, call, 1)
    addArgumentEdge(testId, call, 1)
    addAstEdge(trueId, call, 2)
    addArgumentEdge(trueId, call, 2)
    addAstEdge(falseId, call, 3)
    addArgumentEdge(falseId, call, 3)
    call
  }

  def createCallNode(code: String, callName: String, dispatchType: String, node: Node): NewCall = {
    val call = callNode(node, shortenCode(code), callName, callName, dispatchType, None, Some(Defines.Any))
    diffGraph.addNode(call)
    call
  }

  def createFileNode(fileName: String): NewFile = {
    val fileNode = NewFile().name(fileName)
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
    val methodRef = methodRefNode(functionNode, shortenCode(code), methodFullName, methodFullName)
    diffGraph.addNode(methodRef)
    methodRef
  }

  def createTypeRefNode(code: String, typeFullName: String, classNode: ClassNode): NewTypeRef = {
    val typeRef = typeRefNode(classNode, shortenCode(code), typeFullName)
    diffGraph.addNode(typeRef)
    typeRef
  }

  def createMethodNode(methodName: String, methodFullName: String, functionNode: FunctionNode): NewMethod = {
    val code   = shortenCode(sanitizeCode(functionNode))
    val method = methodNode(functionNode, methodName, code, methodFullName, None, source.filePath)
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
    val code = if (keepWholeCode) { customCode.getOrElse(sanitizeCode(node)) }
    else { shortenCode(customCode.getOrElse(sanitizeCode(node))) }
    val block = blockNode(node, code, Defines.Any)
    diffGraph.addNode(block)
    block
  }

  def createMethodReturnNode(node: Node): NewMethodReturn = {
    val ret = methodReturnNode(node, Defines.Any)
    diffGraph.addNode(ret)
    ret
  }

  def createTypeNode(name: String, fullName: String): NewType = {
    val typ = NewType().name(name).fullName(fullName).typeDeclFullName(fullName)
    diffGraph.addNode(typ)
    typ
  }

  def createBindingNode(): NewBinding = {
    val binding = NewBinding().name("").signature("")
    diffGraph.addNode(binding)
    binding
  }

  def createJumpTarget(caseNode: CaseNode): NewJumpTarget = {
    val name       = if (caseNode.toString().startsWith("case")) "case" else "default"
    val code       = shortenCode(caseNode.toString())
    val jumpTarget = jumpTargetNode(caseNode, name, code, Some(caseNode.getClass.getSimpleName))
    diffGraph.addNode(jumpTarget)
    jumpTarget
  }

  def createControlStructureNode(node: Node, controlStructureType: String): NewControlStructure = {
    val controlStructure = controlStructureNode(node, controlStructureType, shortenCode(source.getString(node)))
    diffGraph.addNode(controlStructure)
    controlStructure
  }

  def createMemberNode(name: String, node: Node, dynamicTypeOption: Option[String]): NewMember = {
    val member = memberNode(node, name, shortenCode(source.getString(node)), Defines.Any, dynamicTypeOption.toList)
    diffGraph.addNode(member)
    member
  }

  def createLocalNode(name: String, typeFullName: String, closureBindingId: Option[String] = None): NewLocal = {
    val local = NodeBuilders.newLocalNode(shortenCode(name), typeFullName, closureBindingId)
    diffGraph.addNode(local)
    local
  }

  def createReturnNode(node: Node): NewReturn = {
    val code = sanitizeCode(node)
    val ret  = returnNode(node, shortenCode(code))
    diffGraph.addNode(ret)
    ret
  }

}

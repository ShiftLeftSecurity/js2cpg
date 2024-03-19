package io.shiftleft.js2cpg.astcreation

import com.oracle.js.parser.TokenType
import com.oracle.js.parser.ir.{
  AccessNode,
  BinaryNode,
  Block,
  BlockExpression,
  BlockStatement,
  BreakNode,
  CallNode,
  CaseNode,
  CatchNode,
  ClassNode,
  ContinueNode,
  DebuggerNode,
  ErrorNode,
  Expression,
  ExpressionStatement,
  ForNode,
  FunctionNode,
  IdentNode,
  IfNode,
  ImportNode,
  IndexNode,
  JoinPredecessorExpression,
  LabelNode,
  LiteralNode,
  Module,
  Node,
  ObjectNode,
  ParameterNode,
  PropertyNode,
  ReturnNode,
  Statement,
  SwitchNode,
  TemplateLiteralNode,
  TernaryNode,
  ThrowNode,
  TryNode,
  UnaryNode,
  VarNode,
  WhileNode,
  WithNode
}
import com.oracle.js.parser.ir.LiteralNode.ArrayLiteralNode
import io.shiftleft.codepropertygraph.generated.nodes.{
  NewBlock,
  NewCall,
  NewControlStructure,
  NewIdentifier,
  NewLocal,
  NewMethod,
  NewMethodRef,
  NewNamespaceBlock,
  NewNode,
  NewTypeDecl,
  NewTypeRef
}
import io.shiftleft.codepropertygraph.generated.{ControlStructureTypes, DispatchTypes, ModifierTypes, Operators}
import io.shiftleft.js2cpg.datastructures.Stack.*
import io.shiftleft.js2cpg.datastructures.scope.Scope
import io.shiftleft.js2cpg.datastructures.OrderTracker
import io.shiftleft.js2cpg.datastructures.scope.MethodScope
import io.shiftleft.js2cpg.datastructures.Parameter
import io.shiftleft.js2cpg.datastructures.scope.BlockScope
import io.shiftleft.js2cpg.datastructures.scope.BlockScopeElement
import io.shiftleft.js2cpg.datastructures.scope.MethodScopeElement
import io.shiftleft.js2cpg.datastructures.scope.ResolvedReference
import io.shiftleft.js2cpg.datastructures.scope.ScopeElement
import io.shiftleft.js2cpg.datastructures.scope.ScopeElementIterator
import io.shiftleft.js2cpg.datastructures.scope.ScopeType
import io.shiftleft.js2cpg.passes.{Defines, EcmaBuiltins, PassHelpers}
import io.shiftleft.js2cpg.passes.PassHelpers.ParamNodeInitKind
import io.shiftleft.js2cpg.parser.{GeneralizingAstVisitor, JsSource}
import overflowdb.BatchedUpdate.DiffGraphBuilder
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

object AstCreator {

  private val logger = LoggerFactory.getLogger(AstCreator.getClass)

  private val VERSION_IMPORT = "import"

  private val VERSION_REQUIRE = "require"

}

class AstCreator(val diffGraph: DiffGraphBuilder, val source: JsSource, val usedIdentNodes: Set[String])
    extends GeneralizingAstVisitor[NewNode]
    with AstNodeBuilder
    with AstEdgeBuilder {

  import AstCreator._

  protected val scope = new Scope()

  // Nested methods are not put in the AST where they are defined.
  // Instead we put them directly under the METHOD in which they are
  // defined. To achieve this we need this extra stack.
  private val methodAstParentStack     = new Stack[NewNode]()
  private val localAstParentStack      = new Stack[NewBlock]()
  private val switchExpressionStack    = new Stack[Expression]()
  private val dynamicInstanceTypeStack = mutable.Stack.empty[String]
  private val metaTypeRefIdStack       = mutable.Stack.empty[NewTypeRef]

  private val functionNodeToNameAndFullName = mutable.HashMap.empty[FunctionNode, (String, String)]

  private val functionFullNames = mutable.HashSet.empty[String]

  private val typeFullNameToPostfix = mutable.HashMap.empty[String, Int]
  private val typeToNameAndFullName = mutable.HashMap.empty[ClassNode, (String, String)]

  private val usedVariableNames = mutable.HashMap.empty[String, Int]

  private def prepareFileWrapperFunction(): NewNamespaceBlock = {
    val fileName       = source.filePath
    val fileNode       = createFileNode(fileName)
    val namespaceBlock = createNamespaceBlockNode(fileName + ":" + Defines.GlobalNamespace)
    addAstEdge(namespaceBlock, fileNode)
    namespaceBlock
  }

  private def addLocalToAst(local: NewLocal): Unit = {
    addAstEdge(local, localAstParentStack.head, 0)
  }

  private def addMethodToAst(method: NewMethod): Unit = {
    addAstEdge(method, methodAstParentStack.head, 0)
  }

  private def addTypeDeclToAst(typeDecl: NewTypeDecl): Unit = {
    addAstEdge(typeDecl, methodAstParentStack.head, 0)
  }

  /** Entry point for converting ASTs with this class.
    *
    * @param programFunction
    *   The function representing an AST. The JS parser always wraps all file content in a function.
    */
  def convert(programFunction: FunctionNode): Unit = {
    methodAstParentStack.push(prepareFileWrapperFunction())
    createImportsAndDependencies(programFunction.getModule)
    programFunction.accept(this)
    createVariableReferenceLinks()
  }

  private def createImportsAndDependencies(module: Module): Unit = {

    if (module == null) {
      return
    }

    module.getImports.forEach { importNode =>
      val groupId = groupIdFromImportNode(importNode)
      importNode.getModuleSpecifier match {
        case null =>
          val defaultBinding = importNode.getImportClause.getDefaultBinding
          if (defaultBinding != null) {
            createDependencyNode(defaultBinding.getName, groupId, VERSION_IMPORT)
            createImportNodeAndAttachToAst(importNode)
          }

          val nameSpaceImport = importNode.getImportClause.getNameSpaceImport
          val namedImports    = importNode.getImportClause.getNamedImports
          if (nameSpaceImport != null) {
            createDependencyNode(nameSpaceImport.getBindingIdentifier.getName, groupId, VERSION_IMPORT)
            createImportNodeAndAttachToAst(importNode)
          } else if (namedImports != null) {
            namedImports.getImportSpecifiers.forEach { namedImport =>
              createDependencyNode(namedImport.getBindingIdentifier.getName, groupId, VERSION_IMPORT)
              createImportNodeAndAttachToAst(importNode)
            }
          }
        case module =>
          createDependencyNode(module.getString, groupId, VERSION_IMPORT)
          createImportNodeAndAttachToAst(importNode)
      }
    }
  }

  private def createImportNodeAndAttachToAst(importNode: ImportNode) = {
    val impNode = createImportNode(importNode)
    methodAstParentStack.headOption.collect { case namespaceBlockNode: NewNamespaceBlock =>
      addAstEdge(impNode, namespaceBlockNode)
    }
  }

  override def visit(breakNode: BreakNode): NewNode = {
    createControlStructureNode(breakNode, ControlStructureTypes.BREAK)
  }

  override def visit(continueNode: ContinueNode): NewNode = {
    createControlStructureNode(continueNode, ControlStructureTypes.CONTINUE)
  }

  private def createIdentifierNode(name: String, lineAndColumnProvider: Node): NewIdentifier = {
    val dynamicInstanceTypeOption = name match {
      case "this" =>
        dynamicInstanceTypeStack.headOption
      case "console" =>
        Some(Defines.Console)
      case "Math" =>
        Some(Defines.Math)
      case _ =>
        None
    }

    createIdentifierNode(name, lineAndColumnProvider, dynamicInstanceTypeOption)
  }

  private def handleDestructingParameter(
    index: Int,
    paramComponents: List[IdentNode],
    initStatements: Option[Statement],
    methodId: NewMethod,
    blockId: NewBlock,
    blockOrder: OrderTracker
  ): Unit = {
    val name =
      PassHelpers.generateUnusedVariableName(usedVariableNames, usedIdentNodes, s"param$index")
    val code_ = paramComponents.map(code).mkString("{", ", ", "}")
    createParameterInNode(name, code_, methodId, paramComponents.head, new OrderTracker(index))
    initStatements match {
      case Some(initExpr: ExpressionStatement) =>
        val destructingAssignmentId =
          convertDestructingAssignment(initExpr.getExpression.asInstanceOf[BinaryNode], Some(name))
        addAstEdge(destructingAssignmentId, blockId, blockOrder)
      case None =>
        paramComponents.foreach { param =>
          val paramName    = param.getName
          val localParamId = createIdentifierNode(paramName, param)
          val paramId      = createIdentifierNode(name, param)
          scope.addVariableReference(name, paramId)

          val localParamLocal = createLocalNode(paramName, Defines.Any)
          addLocalToAst(localParamLocal)
          scope.addVariable(paramName, localParamLocal, MethodScope)
          val keyId    = createFieldIdentifierNode(paramName, param)
          val accessId = createFieldAccessCallNode(paramId, keyId, param)
          val assignmentCallId =
            createAssignmentNode(localParamId, accessId, param)
          addAstEdge(assignmentCallId, blockId, blockOrder)
          blockOrder.inc()
        }
      case _ =>
        logger.debug(s"Unhandled parameter kind: $initStatements")
    }
  }

  private def handleSyntheticParameter(
    index: Int,
    parameter: IdentNode,
    initStatement: Option[Statement],
    methodId: NewMethod,
    blockId: NewBlock,
    blockOrder: OrderTracker
  ): Unit = {
    val name, code = PassHelpers.cleanParameterNodeName(parameter)
    createParameterInNode(name, code, methodId, parameter, new OrderTracker(index))
    initStatement match {
      case Some(initExpr: VarNode) =>
        val paramName    = initExpr.getName.getName
        val localParamId = createIdentifierNode(paramName, initExpr)
        val rhs          = createRhsForConditionalParameterInit(initExpr.getAssignmentSource, name, initExpr)
        val assignmentCallId =
          createAssignmentNode(localParamId, rhs, initExpr)
        addAstEdge(assignmentCallId, blockId, blockOrder)
      case Some(initExpr: ExpressionStatement) =>
        val destructingAssignmentId =
          convertDestructingAssignment(initExpr.getExpression.asInstanceOf[BinaryNode], Some(name))
        addAstEdge(destructingAssignmentId, blockId, blockOrder)
      case None =>
        val paramName       = name
        val localParamId    = createIdentifierNode(paramName, parameter)
        val localParamLocal = createLocalNode(paramName, Defines.Any)
        addLocalToAst(localParamLocal)
        scope.addVariable(paramName, localParamLocal, MethodScope)

        val paramId = createIdentifierNode(name, parameter)
        scope.addVariableReference(name, paramId)

        val keyId = createFieldIdentifierNode(paramName, parameter)

        val accessId = createFieldAccessCallNode(paramId, keyId, parameter)

        val assignmentCallId =
          createAssignmentNode(localParamId, accessId, parameter)
        addAstEdge(assignmentCallId, blockId, blockOrder)
      case _ =>
        logger.debug(s"Unhandled parameter kind: $initStatement")
    }
  }

  private def createFunctionNode(
    functionNode: FunctionNode,
    shouldCreateFunctionReference: Boolean
  ): (Option[NewMethodRef], NewMethod) = {
    val functionBodyStatements = functionNode.getBody.getStatements.asScala.toList

    val syntheticParameters =
      PassHelpers.collectSyntheticParameters(functionBodyStatements)
    val destructingParameters =
      PassHelpers.collectDestructingParameters(functionBodyStatements)

    val (methodName, methodFullName) = calcMethodNameAndFullName(functionNode)

    val methodId = createMethodNode(methodName, methodFullName, functionNode)
    addMethodToAst(methodId)

    if (!functionNode.isProgram) {
      val virtualModifierId = createModifierNode(ModifierTypes.VIRTUAL)
      addAstEdge(virtualModifierId, methodId)
    }

    val methodRefId =
      if (!shouldCreateFunctionReference) {
        None
      } else {
        Some(createMethodRefNode(methodName, methodFullName, functionNode))
      }

    methodAstParentStack.push(methodId)

    val block   = functionNode.getBody
    val blockId = createBlockNode(block, functionNode.isProgram)
    addAstEdge(blockId, methodId, 1)

    val capturingRefId =
      if (shouldCreateFunctionReference) {
        methodRefId
      } else {
        metaTypeRefIdStack.headOption
      }
    scope.pushNewMethodScope(methodFullName, methodName, blockId, capturingRefId)

    val parameterOrderTracker = new OrderTracker(0)
    // We always create an instance parameter because in JS every function could get called with an instance.
    createParameterInNode("this", "this", methodId, functionNode, parameterOrderTracker)
    functionNode.getParameters.forEach { parameter =>
      createParameterInNode(parameter.getName, source.getString(parameter), methodId, parameter, parameterOrderTracker)
    }

    val blockOrder = new OrderTracker()
    localAstParentStack.push(blockId)

    val parameters = mutable.ArrayBuffer.empty[Parameter]

    destructingParameters.foreach { paramComponents =>
      val index =
        PassHelpers.calculateParameterIndex(paramComponents.head, functionBodyStatements) + 1
      val initStatement =
        functionBodyStatements.find(PassHelpers.isConditionallyInitialized(_, paramComponents))
      handleDestructingParameter(index, paramComponents, initStatement, methodId, blockId, blockOrder)
      parameters.addOne(Parameter(paramComponents, initStatement))
    }

    syntheticParameters.foreach { parameter =>
      val paramComponents = List(parameter)
      val index           = PassHelpers.calculateParameterIndex(parameter, functionBodyStatements) + 1
      val initStatement =
        functionBodyStatements.find(PassHelpers.isConditionallyInitialized(_, paramComponents))
      handleSyntheticParameter(index, parameter, initStatement, methodId, blockId, blockOrder)
      parameters.addOne(Parameter(paramComponents, initStatement))
    }

    val methodReturnId =
      createMethodReturnNode(functionNode)
    addAstEdge(methodReturnId, methodId, 2)

    val filteredFunctionBodyStatements =
      functionBodyStatements.filterNot(PassHelpers.isSynthetic(_, destructingParameters.flatten ++ syntheticParameters))
    visitStatements(
      filteredFunctionBodyStatements.asJava,
      statementId => {
        addAstEdge(statementId, blockId, blockOrder)
      }
    )
    localAstParentStack.pop()

    scope.popScope()

    methodAstParentStack.pop()

    createFunctionTypeAndTypeDecl(functionNode, methodId, methodAstParentStack.head, methodName, methodFullName)

    (methodRefId, methodId)
  }

  private def visitStatements[T](statements: java.util.List[Statement], handler: NewNode => T): Unit = {
    statements.forEach { statement =>
      val statementId = statement.accept(this)
      handler(statementId)
    }
  }

  override def visit(blockExpression: BlockExpression): NewNode = {
    createRealBlock(blockExpression.getBlock)
  }

  override def visit(debuggerNode: DebuggerNode): NewNode = {
    // If no debugging is available, the debugger statement has no effect.
    createUnknownNode(debuggerNode)
  }

  override def visit(functionNode: FunctionNode): NewNode = {
    val functionNodeId = createFunctionNode(
      functionNode,
      // For the outer program function (isProgram) and for class methods (isMethod) we
      // do not want to create method references because there is no using statement in these
      // cases.
      !(functionNode.isProgram || functionNode.isMethod)
    )
    functionNodeId._1.orNull
  }

  private def createFunctionTypeAndTypeDecl(
    node: Node,
    methodId: NewMethod,
    parentNodeId: NewNode,
    methodName: String,
    methodFullName: String
  ): Unit = {

    createTypeNode(methodName, methodFullName)

    val astParentType     = parentNodeId.label
    val astParentFullName = parentNodeId.properties("FULL_NAME").toString

    val functionTypeDeclId =
      createTypeDeclNode(node, methodName, methodFullName, astParentType, astParentFullName, Some(Defines.Any))
    addTypeDeclToAst(functionTypeDeclId)

    // Problem for https://github.com/ShiftLeftSecurity/codescience/issues/3626 here.
    // As the type (thus, the signature) of the function node is unknown (i.e., ANY*)
    // we can't generate the correct binding with signature.
    val functionBindingId = createBindingNode()
    addBindsEdge(functionBindingId, functionTypeDeclId)

    addRefEdge(methodId, functionBindingId)
  }

  override def visit(classNode: ClassNode): NewNode = {
    val (typeName, typeFullName) = calcTypeNameAndFullName(classNode)
    val metaTypeName             = s"$typeName<meta>"
    val metaTypeFullName         = s"$typeFullName<meta>"

    createTypeNode(typeName, typeFullName)

    // We do not need to look at classNode.getClassHeritage because
    // the CPG only allows us to encode inheriting from fully known
    // types. Since in JS we "inherit" from a variable which would
    // need to be resolved first, we for now dont handle the class
    // hierarchy.
    val astParentType     = methodAstParentStack.head.label
    val astParentFullName = methodAstParentStack.head.properties("FULL_NAME").toString

    val typeDeclId =
      createTypeDeclNode(classNode, typeName, typeFullName, astParentType, astParentFullName, inheritsFrom = None)

    createTypeNode(metaTypeName, metaTypeFullName)

    val metaTypeDeclId =
      createTypeDeclNode(
        classNode,
        metaTypeName,
        metaTypeFullName,
        astParentType,
        astParentFullName,
        inheritsFrom = None
      )

    addTypeDeclToAst(typeDeclId)
    addTypeDeclToAst(metaTypeDeclId)

    val metaTypeRefId =
      createTypeRefNode(s"class $typeName", metaTypeFullName, classNode)

    methodAstParentStack.push(typeDeclId)
    dynamicInstanceTypeStack.push(typeFullName)
    metaTypeRefIdStack.push(metaTypeRefId)

    // In case there is no user-written constructor the JS parser creates
    // an empty one automatically. Hence, the following is safe:
    val constructor   = classNode.getConstructor.getValue.asInstanceOf[FunctionNode]
    val constructorId = createFunctionNode(constructor, shouldCreateFunctionReference = false)._2

    val constructorBindingId = createBindingNode()
    addBindsEdge(constructorBindingId, metaTypeDeclId)

    addRefEdge(constructorId, constructorBindingId)

    val memberOrderTracker = new OrderTracker()
    classNode.getClassElements.forEach { classElement =>
      val memberName = classElement.getKeyName

      if (memberName != null) {
        val memberId = classElement match {
          case property if property.getValue.isInstanceOf[FunctionNode] =>
            val function = property.getValue.asInstanceOf[FunctionNode]
            function.accept(this)

            // A function full name and its corresponding type full name are
            // identical.
            val functionFullName        = calcMethodNameAndFullName(function)._2
            val dynamicTypeHintFullName = Some(functionFullName)
            createMemberNode(memberName, classElement, dynamicTypeHintFullName)
          case _ =>
            createMemberNode(memberName, classElement, dynamicTypeOption = None)
        }

        if (classElement.isStatic) {
          // Static member belong to the meta class.
          addAstEdge(memberId, metaTypeDeclId, memberOrderTracker)
        } else {
          addAstEdge(memberId, typeDeclId, memberOrderTracker)
        }
      }
    }

    methodAstParentStack.pop()
    dynamicInstanceTypeStack.pop()
    metaTypeRefIdStack.pop()

    metaTypeRefId
  }

  override def visit(joinPredecessorExpression: JoinPredecessorExpression): NewNode = {
    joinPredecessorExpression.getExpression.accept(this)
  }

  override def visit(ifNode: IfNode): NewNode = {
    val ifNodeId = createControlStructureNode(ifNode, ControlStructureTypes.IF)

    Option(ifNode.getTest).foreach { testNode =>
      val testId = testNode.accept(this)
      addAstEdge(testId, ifNodeId, 1)
      addConditionEdge(testId, ifNodeId)
    }
    Option(ifNode.getPass).foreach { passNode =>
      val passId = passNode.accept(this)
      addAstEdge(passId, ifNodeId, 2)
    }
    Option(ifNode.getFail).foreach { failNode =>
      val failId = failNode.accept(this)
      addAstEdge(failId, ifNodeId, 3)
    }

    ifNodeId
  }

  private def handleCallNodeArgs(
    callNode: CallNode,
    receiverId: NewNode,
    baseId: NewNode,
    functionBaseId: NewNode,
    functionPropertyId: Option[NewNode]
  ): NewCall = {
    val argIds = callNode.getArgs.asScala.map(_.accept(this))

    val baseCode = codeOf(functionBaseId)
    val propertyCode = functionPropertyId match {
      case Some(id) => "." + codeOf(id)
      case None     => ""
    }

    val argsCode = argIds.map(codeOf).mkString("(", ", ", ")")
    val code     = s"$baseCode$propertyCode$argsCode"

    val callId =
      createCallNode(code, "", DispatchTypes.DYNAMIC_DISPATCH, callNode)
    val orderTracker    = new OrderTracker(0)
    val argIndexTracker = new OrderTracker(0)

    addAstEdge(receiverId, callId, orderTracker)
    addReceiverEdge(receiverId, callId)

    addAstEdge(baseId, callId, orderTracker)
    addArgumentEdge(baseId, callId, argIndexTracker)

    argIds.foreach { argId =>
      addAstEdge(argId, callId, orderTracker)
      addArgumentEdge(argId, callId, argIndexTracker)
    }

    callId
  }

  private def createBuiltinStaticCall(callNode: CallNode, methodFullName: String): NewCall = {
    val methodName =
      callNode.getFunction match {
        case accessNode: AccessNode =>
          accessNode.getProperty
        case identNode: IdentNode =>
          identNode.getName
      }
    val callId          = createStaticCallNode(callNode.toString(), methodName, methodFullName, callNode)
    val orderTracker    = new OrderTracker()
    val argIndexTracker = new OrderTracker()
    callNode.getArgs.forEach { arg =>
      val argId = arg.accept(this)
      addAstEdge(argId, callId, orderTracker)
      addArgumentEdge(argId, callId, argIndexTracker)
    }

    callId
  }

  override def visit(callNode: CallNode): NewNode = {
    val methodFullName = callNode.getFunction.toString

    val callId = if (globalBuiltins.contains(methodFullName)) {
      createBuiltinStaticCall(callNode, methodFullName)
    } else {
      val (functionBaseId, functionPropertyId, receiverId, baseId) = callNode.getFunction match {
        case functionAccessNode: AccessNode =>
          // "this" argument is coming from source.
          functionAccessNode.getBase match {
            case baseIdentNode: IdentNode =>
              // TODO The check for IdentNode is too restrictive.
              // We could go into this branch for all bases which do not contain side effects(CallNode).
              // But in this case we would visit the base AST twice which is currently not supported by
              // our NodeIdentifier schema. We thus, for now, need to live with the overzealous creation
              // of tmp variables(aliases). E.g.: a.b.c() get an intermediate tmp variable which is
              // unnecessary.

              // The base is an identifier so we do not need to create a tmp variable.
              val receiverId = functionAccessNode.accept(this)

              val baseId = createIdentifierNode(baseIdentNode.getName, baseIdentNode)
              scope.addVariableReference(baseIdentNode.getName, baseId)

              (receiverId, None, receiverId, baseId)
            case base =>
              // Base is complex so we need a tmp variable.
              val tmpVarName =
                PassHelpers.generateUnusedVariableName(usedVariableNames, usedIdentNodes, "_tmp")

              val baseTmpId = createIdentifierNode(tmpVarName, base)
              scope.addVariableReference(tmpVarName, baseTmpId)

              val baseId = base.accept(this)

              val tmpAssignmentId = createAssignmentNode(baseTmpId, baseId, base, withParenthesis = true)

              val memberId =
                createFieldIdentifierNode(functionAccessNode.getProperty, functionAccessNode)

              val fieldAccessId = createFieldAccessCallNode(tmpAssignmentId, memberId, functionAccessNode)

              val thisTmpId = createIdentifierNode(tmpVarName, functionAccessNode)
              scope.addVariableReference(tmpVarName, thisTmpId)

              (baseId, Some(memberId), fieldAccessId, thisTmpId)
          }
        case _ =>
          val receiverId = callNode.getFunction.accept(this)

          // We need to create an synthetic this argument.
          val thisId = createIdentifierNode("this", callNode)
          scope.addVariableReference("this", thisId)

          (receiverId, None, receiverId, thisId)
      }

      handleCallNodeArgs(callNode, receiverId, baseId, functionBaseId, functionPropertyId)
    }

    callId
  }

  override def visit(blockStatement: BlockStatement): NewNode = {
    blockStatement.getBlock.accept(this)
  }

  override def visit(whileNode: WhileNode): NewNode = {
    val controlStructureType = if (whileNode.isDoWhile) {
      ControlStructureTypes.DO
    } else {
      ControlStructureTypes.WHILE
    }
    val whileNodeId = createControlStructureNode(whileNode, controlStructureType)

    if (whileNode.isDoWhile) {
      val bodyId = whileNode.getBody.accept(this)
      addAstEdge(bodyId, whileNodeId, 1)

      val testId = whileNode.getTest.accept(this)
      addAstEdge(testId, whileNodeId, 2)
      addConditionEdge(testId, whileNodeId)
    } else {
      val testId = whileNode.getTest.accept(this)
      addAstEdge(testId, whileNodeId, 1)
      addConditionEdge(testId, whileNodeId)

      val bodyId = whileNode.getBody.accept(this)
      addAstEdge(bodyId, whileNodeId, 2)
    }

    whileNodeId
  }

  private def createForNode(forNode: ForNode): NewControlStructure = {
    val forNodeId = createControlStructureNode(forNode, ControlStructureTypes.FOR)

    Option(forNode.getInit).foreach { initNode =>
      val initNodeId = initNode.accept(this)
      addAstEdge(initNodeId, forNodeId, 1)
    }

    val testNodeId = forNode.getTest match {
      case null =>
        // If the for condition is empty, this ensures that there is always a condition (true) present.
        val testNodeId =
          createLiteralNode("true", forNode, Some(Defines.Boolean))
        testNodeId
      case testNode if testNode.getExpression == null =>
        // If the for condition is empty, this ensures that there is always a condition (true) present.
        val testNodeId =
          createLiteralNode("true", forNode, Some(Defines.Boolean))
        testNodeId
      // The test of a forNode can be a JoinPredecessorExpression which does not wrap any expression.
      // This only happens for "for (x in y)" style loops.
      // We need to check this ahead of visit because during visit we would be forced to return some
      // kind of expression identifier.
      case testNode if testNode.getExpression != null =>
        testNode.accept(this)
    }
    addAstEdge(testNodeId, forNodeId, 2)

    Option(forNode.getModify).foreach { modifyNode =>
      val modifyNodeId = modifyNode.accept(this)
      addAstEdge(modifyNodeId, forNodeId, 3)
    }

    if (forNode.getBody.getStatementCount != 0) {
      val bodyId = forNode.getBody.accept(this)
      addAstEdge(bodyId, forNodeId, 4)
    }

    forNodeId
  }

  /** De-sugaring from:
    *
    * for (var i in arr) { // body }
    *
    * to:
    *
    * { var _iterator = Object.keys(arr)[Symbol.iterator](); var _result; var i; while (!(_result =
    * _iterator.next()).done) { i = _result.value; // body } }
    */
  private def createForInOrOfNode(forNode: ForNode): NewBlock = {
    // surrounding block:
    val blockOrder = new OrderTracker()
    val blockId    = createBlockNode(forNode)
    scope.pushNewBlockScope(blockId)
    localAstParentStack.push(blockId)

    val collection     = forNode.getModify
    val collectionName = collection.toString()

    // _iterator assignment:
    val iteratorName =
      PassHelpers.generateUnusedVariableName(usedVariableNames, usedIdentNodes, "_iterator")
    val iteratorLocalId = createLocalNode(iteratorName, Defines.Any)
    addLocalToAst(iteratorLocalId)

    val iteratorId = createIdentifierNode(iteratorName, forNode)

    val callId = createCallNode(
      "Object.keys(" + collectionName + ")[Symbol.iterator]()",
      "",
      DispatchTypes.DYNAMIC_DISPATCH,
      forNode
    )

    val thisId = createIdentifierNode("this", forNode)

    val indexCallId = createCallNode(
      "Object.keys(" + collectionName + ")[Symbol.iterator]",
      Operators.indexAccess,
      DispatchTypes.STATIC_DISPATCH,
      forNode
    )

    val objectKeysCallId =
      createStaticCallNode("Object.keys(" + collectionName + ")", "keys", "Object.keys", forNode)

    val argId = collection.accept(this)
    addAstEdge(argId, objectKeysCallId, 1)
    addArgumentEdge(argId, objectKeysCallId, 1)

    val indexBaseId = createIdentifierNode("Symbol", forNode)

    val indexMemberId = createFieldIdentifierNode("iterator", forNode)

    val indexAccessId =
      createFieldAccessCallNode(indexBaseId, indexMemberId, forNode)

    addAstEdge(objectKeysCallId, indexCallId, 1)
    addArgumentEdge(objectKeysCallId, indexCallId, 1)
    addAstEdge(indexAccessId, indexCallId, 2)
    addArgumentEdge(indexAccessId, indexCallId, 2)

    addAstEdge(indexCallId, callId, 0)
    addReceiverEdge(indexCallId, callId)

    addAstEdge(thisId, callId, 1)
    addArgumentEdge(thisId, callId, 0)

    val iteratorAssignmentId =
      createCallNode(
        iteratorName + " = " + "Object.keys(" + collectionName + ")[Symbol.iterator]()",
        Operators.assignment,
        DispatchTypes.STATIC_DISPATCH,
        forNode
      )

    addAstEdge(iteratorId, iteratorAssignmentId, 1)
    addArgumentEdge(iteratorId, iteratorAssignmentId, 1)
    addAstEdge(callId, iteratorAssignmentId, 2)
    addArgumentEdge(callId, iteratorAssignmentId, 2)
    addAstEdge(iteratorAssignmentId, blockId, blockOrder)

    // _result:
    val resultName =
      PassHelpers.generateUnusedVariableName(usedVariableNames, usedIdentNodes, "_result")
    val resultLocalId = createLocalNode(resultName, Defines.Any)
    addLocalToAst(resultLocalId)
    val resultId = createIdentifierNode(resultName, forNode)
    addAstEdge(resultId, blockId, blockOrder)

    // loop variable:
    val loopVariableName    = forNode.getInit.toString()
    val loopVariableLocalId = createLocalNode(loopVariableName, Defines.Any)
    addLocalToAst(loopVariableLocalId)
    val loopVariableId = createIdentifierNode(loopVariableName, forNode.getInit)
    addAstEdge(loopVariableId, blockId, blockOrder)

    // while loop:
    val whileLoopId =
      createControlStructureNode(forNode, ControlStructureTypes.WHILE)
    addAstEdge(whileLoopId, blockId, blockOrder)

    // while loop test:
    val testCallId = createCallNode(
      "!(" + resultName + " = " + iteratorName + ".next()).done",
      Operators.not,
      DispatchTypes.STATIC_DISPATCH,
      forNode
    )

    val doneBaseId = createCallNode(
      "(" + resultName + " = " + iteratorName + ".next())",
      Operators.assignment,
      DispatchTypes.STATIC_DISPATCH,
      forNode
    )

    val lhsId = createIdentifierNode(resultName, forNode)

    val rhsId = createCallNode(iteratorName + ".next()", "", DispatchTypes.DYNAMIC_DISPATCH, forNode)

    val nextBaseId = createIdentifierNode(iteratorName, forNode)

    val nextMemberId = createFieldIdentifierNode("next", forNode)

    val nextReceiverId =
      createFieldAccessCallNode(nextBaseId, nextMemberId, forNode)

    val thisNextId = createIdentifierNode(iteratorName, forNode)

    addAstEdge(nextReceiverId, rhsId, 0)
    addReceiverEdge(nextReceiverId, rhsId)

    addAstEdge(thisNextId, rhsId, 1)
    addArgumentEdge(thisNextId, rhsId, 0)

    addAstEdge(lhsId, doneBaseId, 1)
    addArgumentEdge(lhsId, doneBaseId, 1)
    addAstEdge(rhsId, doneBaseId, 2)
    addArgumentEdge(rhsId, doneBaseId, 2)

    val doneMemberId = createFieldIdentifierNode("done", forNode)

    val testId = createFieldAccessCallNode(doneBaseId, doneMemberId, forNode)

    addAstEdge(testId, testCallId, 1)
    addArgumentEdge(testId, testCallId, 1)

    addAstEdge(testCallId, whileLoopId, 1)
    addConditionEdge(testCallId, whileLoopId)

    // while loop variable assignment:
    val whileLoopVariableId =
      createIdentifierNode(loopVariableName, forNode)

    val baseId = createIdentifierNode(resultName, forNode)

    val memberId = createFieldIdentifierNode("value", forNode)

    val accessId =
      createFieldAccessCallNode(baseId, memberId, forNode)

    val loopVariableAssignmentId = createCallNode(
      loopVariableName + " = " + resultName + ".value",
      Operators.assignment,
      DispatchTypes.STATIC_DISPATCH,
      forNode
    )

    addAstEdge(whileLoopVariableId, loopVariableAssignmentId, 1)
    addArgumentEdge(whileLoopVariableId, loopVariableAssignmentId, 1)
    addAstEdge(accessId, loopVariableAssignmentId, 2)
    addArgumentEdge(accessId, loopVariableAssignmentId, 2)

    val whileLoopBlockOrder = new OrderTracker()
    val whileLoopBlockId    = createBlockNode(forNode)
    scope.pushNewBlockScope(whileLoopBlockId)
    localAstParentStack.push(whileLoopBlockId)

    addAstEdge(loopVariableAssignmentId, whileLoopBlockId, whileLoopBlockOrder)

    // while loop block:
    if (forNode.getBody.getStatementCount != 0) {
      val bodyId = forNode.getBody.accept(this)
      addAstEdge(bodyId, whileLoopBlockId, whileLoopBlockOrder)
    }

    addAstEdge(whileLoopBlockId, whileLoopId, 2)
    scope.popScope()
    localAstParentStack.pop()

    // end surrounding block:
    scope.popScope()
    localAstParentStack.pop()
    blockId
  }

  override def visit(forNode: ForNode): NewNode = {
    if (forNode.isForInOrOf) {
      createForInOrOfNode(forNode)
    } else {
      createForNode(forNode)
    }
  }

  override def visit(expressionStatement: ExpressionStatement): NewNode = {
    expressionStatement.getExpression.accept(this)
  }

  private def createRealBlock(block: Block): NewBlock = {
    val blockId = createBlockNode(block)

    val orderTracker = new OrderTracker()
    scope.pushNewBlockScope(blockId)

    localAstParentStack.push(blockId)
    visitStatements(
      block.getStatements,
      statementId => {
        addAstEdge(statementId, blockId, orderTracker)
      }
    )
    localAstParentStack.pop()

    scope.popScope()

    blockId
  }

  // We do not get here for specially handled method top level blocks.
  override def visit(block: Block): NewNode = {
    val realBlock = source.getString(block) == "{"

    if (realBlock) {
      createRealBlock(block)
    } else {
      if (block.getStatementCount != 0) {
        block.getStatements.get(0) match {
          case varNode: VarNode if varNode.isLet && varNode.getName.getName == ":switch" =>
            // For switch statements the JS parser generates a synthetic let: let :switch = expr
            // The following statement is then the SwitchNode:
            switchExpressionStack.push(varNode.getAssignmentSource)
            val switchNodeId = block.getStatements.get(1).accept(this)
            switchExpressionStack.pop()
            switchNodeId
          case _ =>
            val blockParts = new NewCompositeNode()
            visitStatements(
              block.getStatements,
              statementId => {
                blockParts.add(statementId)
              }
            )
            blockParts
        }
      } else {
        new NewCompositeNode()
      }
    }
  }

  /** De-sugar ArrayLiteralNodes like follows:
    *
    * [] // empty case
    *
    * to
    *
    * Array()
    *
    * and
    *
    * [elem0, ..., elemN] // default case
    *
    * to
    *
    * { _tmp = Array(); _tmp.push(elem0); ... _tmp.push(elemN); _tmp; }
    */
  private def createArrayLiteralNode(arrayLiteralNode: ArrayLiteralNode): NewNode = {
    if (arrayLiteralNode.getElementExpressions.isEmpty) {
      val arrayCallId = createCallNode(
        EcmaBuiltins.arrayFactory + "()",
        EcmaBuiltins.arrayFactory,
        DispatchTypes.STATIC_DISPATCH,
        arrayLiteralNode
      )
      arrayCallId
    } else {
      val blockId = createBlockNode(arrayLiteralNode)

      scope.pushNewBlockScope(blockId)
      val blockOrder = new OrderTracker()
      localAstParentStack.push(blockId)

      val tmpName    = PassHelpers.generateUnusedVariableName(usedVariableNames, usedIdentNodes, "_tmp")
      val localTmpId = createLocalNode(tmpName, Defines.Any)
      addLocalToAst(localTmpId)

      val tmpArrayId = createIdentifierNode(tmpName, arrayLiteralNode)

      val arrayCallId = createCallNode(
        EcmaBuiltins.arrayFactory + "()",
        EcmaBuiltins.arrayFactory,
        DispatchTypes.STATIC_DISPATCH,
        arrayLiteralNode
      )

      val assignmentTmpArrayCallId =
        createAssignmentNode(tmpArrayId, arrayCallId, arrayLiteralNode)

      addAstEdge(assignmentTmpArrayCallId, blockId, blockOrder)

      arrayLiteralNode.getElementExpressions.forEach {
        case element if element != null =>
          val elementId = element.accept(this)

          val pushCallId =
            createCallNode(tmpName + s".push(${codeOf(elementId)})", "", DispatchTypes.DYNAMIC_DISPATCH, element)

          val nextBaseId = createIdentifierNode(tmpName, element)

          val nextMemberId = createFieldIdentifierNode("push", element)

          val nextReceiverId =
            createFieldAccessCallNode(nextBaseId, nextMemberId, element)

          val thisPushId = createIdentifierNode(tmpName, element)

          addAstEdge(nextReceiverId, pushCallId, 0)
          addReceiverEdge(nextReceiverId, pushCallId)

          addAstEdge(thisPushId, pushCallId, 1)
          addArgumentEdge(thisPushId, pushCallId, 0)

          addAstEdge(elementId, pushCallId, 2)
          addArgumentEdge(elementId, pushCallId, 1)

          addAstEdge(pushCallId, blockId, blockOrder)
        case _ => // skip
      }

      val tmpArrayReturnId = createIdentifierNode(tmpName, arrayLiteralNode)
      addAstEdge(tmpArrayReturnId, blockId, blockOrder)

      scope.popScope()
      localAstParentStack.pop()

      blockId
    }
  }

  override def visit(literalNode: LiteralNode[_]): NewNode = {
    literalNode match {
      case arrayLiteralNode: ArrayLiteralNode =>
        createArrayLiteralNode(arrayLiteralNode)
      case _ =>
        val (code, dynamicTypeOption) = literalNode match {
          case bool if literalNode.getValue.isInstanceOf[Boolean] =>
            // For boolean nodes we here enforce that we get a "true" or "false".
            // This is required because source.getCode(literalNode) can be an empty string
            // for constructs like: for(;;)
            (bool.getString, Some(Defines.Boolean))
          case string if literalNode.isString =>
            // Some string values are artificially created and thus source.getCode() would
            // result in misleading code fields.
            ("\"" + string.getString + "\"", Some(Defines.String))
          case _ if literalNode.getValue == null =>
            ("null", Some(Defines.Null))
          case obj =>
            (obj.getString, None)
        }
        createLiteralNode(code, literalNode, dynamicTypeOption)
    }
  }

  override def visit(identNode: IdentNode): NewNode = {
    val identId = createIdentifierNode(identNode.getName, identNode)
    scope.addVariableReference(identNode.getName, identId)
    identId
  }

  override def visit(accessNode: AccessNode): NewNode = {
    val baseId   = accessNode.getBase.accept(this)
    val memberId = createFieldIdentifierNode(accessNode.getProperty, accessNode)
    val accessId = createFieldAccessCallNode(baseId, memberId, accessNode)
    accessId
  }

  private def handleSwitchCase(caseNode: CaseNode, blockId: NewBlock, blockOrder: OrderTracker): Unit = {
    val labelId = createJumpTarget(caseNode)
    addAstEdge(labelId, blockId, blockOrder)

    Option(caseNode.getTest).foreach { testExpr =>
      val testId = testExpr.accept(this)
      addAstEdge(testId, blockId, blockOrder)
    }

    visitStatements(
      caseNode.getStatements,
      statementId => {
        addAstEdge(statementId, blockId, blockOrder)
      }
    )
  }

  override def visit(switchNode: SwitchNode): NewNode = {
    val switchNodeId =
      createControlStructureNode(switchNode, ControlStructureTypes.SWITCH)

    // We need to get the to be switched upon expression from our switchExpressionStack because
    // the compiler generates a synthetic let: 'let :switch = expr' and thus switchNode.getExpression
    // just returns an IdentNode to :switch.
    val switchExpression   = switchExpressionStack.head
    val switchExpressionId = switchExpression.accept(this)
    addAstEdge(switchExpressionId, switchNodeId, 1)
    addConditionEdge(switchExpressionId, switchNodeId)

    val blockId = createBlockNode(switchNode)
    scope.pushNewBlockScope(blockId)
    localAstParentStack.push(blockId)

    val blockOrder = new OrderTracker()
    switchNode.getCases.forEach { caseNode =>
      handleSwitchCase(caseNode, blockId, blockOrder)
    }

    addAstEdge(blockId, switchNodeId, 2)
    scope.popScope()
    localAstParentStack.pop()

    switchNodeId
  }

  override def visit(parameterNode: ParameterNode): NewNode = {
    val parameterNodeId = createIdentifierNode("arguments", parameterNode)

    val indexId = createLiteralNode(parameterNode.getIndex.toString, parameterNode, Some(Defines.Number))

    val accessId =
      createIndexAccessNode(parameterNodeId, indexId, parameterNode)

    accessId
  }

  private def createRhsForConditionalParameterInit(initExpression: Expression, varNode: VarNode): NewCall = {
    createRhsForConditionalParameterInit(initExpression, varNode.getName.getName, varNode)
  }

  private def createRhsForConditionalParameterInit(
    initExpression: Expression,
    propertyNode: PropertyNode,
    name: Option[String]
  ): NewCall = {
    val keyName = name.getOrElse(Option(propertyNode.getKeyName) match {
      case Some(name) => name
      case None =>
        PassHelpers.generateUnusedVariableName(usedVariableNames, usedIdentNodes, "_anon_member")
    })
    createRhsForConditionalParameterInit(initExpression, keyName, propertyNode)
  }

  private def createRhsForConditionalParameterInit(initExpression: Expression, keyName: String, node: Node): NewCall = {
    val ternaryNode = initExpression.asInstanceOf[TernaryNode]
    val testId = {
      ternaryNode.getTest match {
        case binTestExpr: BinaryNode =>
          val lhsId = createIdentifierNode(keyName, node)
          scope.addVariableReference(keyName, lhsId)

          val rhsId = binTestExpr.getRhs.accept(this)

          val testCallId = createEqualsCallNode(lhsId, rhsId, binTestExpr)

          testCallId
        case otherExpr => otherExpr.accept(this)
      }
    }

    val trueId = ternaryNode.getTrueExpression.accept(this)

    val falseId = {
      ternaryNode.getFalseExpression.getExpression match {
        case paramNode: ParameterNode if paramNode.toString().startsWith("arguments") =>
          val initId = createIdentifierNode(keyName, node)
          scope.addVariableReference(keyName, initId)
          initId
        case _ => ternaryNode.getFalseExpression.accept(this)
      }
    }
    createTernaryNode(testId, trueId, falseId, ternaryNode)
  }

  private def createDependencyNodeForRequire(name: String, node: Node): Unit = {
    PassHelpers
      .getRequire(node)
      .foreach(id => createDependencyNode(name, id, VERSION_REQUIRE))
  }

  private def createVarNodeParamNodeInitKindFalse(varNode: VarNode, assignmentSource: Expression): NewNode = {
    val (typeFullName, code) =
      if (varNode.isFunctionDeclaration) {
        val functionNode        = varNode.getInit.asInstanceOf[FunctionNode]
        val (_, methodFullName) = calcMethodNameAndFullName(functionNode)
        (methodFullName, varNode.toString())
      } else {
        (Defines.Any, "")
      }

    val varId = createLocalNode(varNode.getName.getName, typeFullName)
    addLocalToAst(varId)
    val scopeType = if (varNode.isLet) {
      BlockScope
    } else {
      MethodScope
    }
    scope.addVariable(varNode.getName.getName, varId, scopeType)

    if (varNode.isAssignment) {
      val destId   = varNode.getAssignmentDest.accept(this)
      val sourceId = assignmentSource.accept(this)

      (destId, assignmentSource) match {
        case (ident: NewIdentifier, call: CallNode) =>
          createDependencyNodeForRequire(ident.name, call)
        case (ident: NewIdentifier, accessNode: AccessNode) =>
          createDependencyNodeForRequire(ident.name, accessNode)
        case _ => // no require call, we do nothing
      }

      val assigmentCallId =
        createAssignmentNode(destId, sourceId, varNode, customCode = code)
      assigmentCallId
    } else {
      new NewCompositeNode()
    }
  }

  private def createVarNode(varNode: VarNode): NewNode = {
    PassHelpers.initializedViaParameterNode(varNode) match {
      case ParamNodeInitKind.CONDITIONAL =>
        val destId = varNode.getAssignmentDest.accept(this)
        val rhsId  = createRhsForConditionalParameterInit(varNode.getInit, varNode)
        val assigmentCallId =
          createAssignmentNode(destId, rhsId, varNode)
        assigmentCallId
      case ParamNodeInitKind.PLAIN =>
        // We get here for all parameters of functions with at least one default parameter value
        // which themselves do not have default values.
        // We can skip the artificially created `paramName = parameter[i]` VarNode because
        // we reconstructed the methods parameter list in the function node handler.
        new NewCompositeNode()
      case ParamNodeInitKind.FALSE =>
        createVarNodeParamNodeInitKindFalse(varNode, varNode.getAssignmentSource)
    }
  }

  override def visit(varNode: VarNode): NewNode = {
    PassHelpers.getClassDeclaration(varNode) match {
      case Some(classNode) =>
        createVarNodeParamNodeInitKindFalse(varNode, classNode)
      case None => createVarNode(varNode)
    }
  }

  override def visit(binaryNode: BinaryNode): NewNode = {
    (binaryNode.tokenType(), binaryNode.getLhs) match {
      case (_ @(TokenType.ASSIGN | TokenType.ASSIGN_INIT), _ @(_: ObjectNode | _: ArrayLiteralNode)) =>
        convertDestructingAssignment(binaryNode, None)
      case _ if binaryNode.tokenType() == TokenType.COMMARIGHT =>
        convertCommaOp(binaryNode)
      case _ => convertSimpleBinaryOp(binaryNode)
    }
  }

  private def convertDestructingAssignment(assignment: BinaryNode, keyName: Option[String]): NewBlock = {
    val rhs = assignment.getRhs

    val blockOrder = new OrderTracker()
    val localTmpName =
      PassHelpers.generateUnusedVariableName(usedVariableNames, usedIdentNodes, "_tmp")

    val blockId = createBlockNode(assignment)
    scope.pushNewBlockScope(blockId)
    localAstParentStack.push(blockId)

    val localId = createLocalNode(localTmpName, Defines.Any)
    addLocalToAst(localId)

    val tmpId = createIdentifierNode(localTmpName, rhs)

    val rhsId =
      if (
        rhs.isInstanceOf[TernaryNode] &&
        assignment.getLhs.isInstanceOf[ObjectNode] &&
        assignment.getLhs
          .asInstanceOf[ObjectNode]
          .getElements
          .get(0)
          .isInstanceOf[PropertyNode]
      ) {
        createRhsForConditionalParameterInit(
          rhs,
          assignment.getLhs.asInstanceOf[ObjectNode].getElements.get(0),
          keyName
        )
      } else {
        rhs.accept(this)
      }

    val assignmentTmpCallId =
      createAssignmentNode(tmpId, rhsId, rhs)

    addAstEdge(assignmentTmpCallId, blockId, blockOrder)

    def convertDestructingElement(element: Node, index: Int): NewNode = {
      element match {
        case identNode: IdentNode =>
          val elementId        = identNode.accept(this)
          val fieldAccessTmpId = createIdentifierNode(localTmpName, identNode)
          val indexId          = createLiteralNode(index.toString, identNode, Some(Defines.Number))
          val accessId =
            createIndexAccessNode(fieldAccessTmpId, indexId, identNode)
          val assignmentCallId =
            createAssignmentNode(elementId, accessId, identNode)
          assignmentCallId
        case propertyNode: PropertyNode
            if propertyNode.getValue == null && AstHelpers.getUnaryOperation(
              propertyNode.getKey.tokenType().toString
            ) == "<operator>.spreadObject" =>
          // TODO: how to handle spread objects here?
          logger.debug(s"Using a spread object for object deconstructing is not yet supported! (${code(assignment)})")
          val unknownId = createUnknownNode(propertyNode)
          unknownId
        case propertyNode: PropertyNode =>
          val valueId          = propertyNode.getValue.accept(this)
          val fieldAccessTmpId = createIdentifierNode(localTmpName, propertyNode)
          val keyId            = createPropertyKeyNode(propertyNode)
          val accessId         = createFieldAccessCallNode(fieldAccessTmpId, keyId, propertyNode.getKey)
          val assignmentCallId =
            createAssignmentNode(valueId, accessId, propertyNode)
          assignmentCallId
      }

    }

    def convertDestructingElementWithDefault(element: Node, index: Int): NewNode = {
      val (lhsId, testId, trueId, falseId) = element match {
        case binaryNode: BinaryNode =>
          val lhsId = binaryNode.getLhs.accept(this)
          val rhsId = binaryNode.getRhs.accept(this)
          val testId = {
            val fieldAccessTmpId =
              createIdentifierNode(localTmpName, binaryNode)

            val indexId = createLiteralNode(index.toString, binaryNode, Some(Defines.Number))

            val accessId =
              createIndexAccessNode(fieldAccessTmpId, indexId, binaryNode)

            val voidCallId =
              createCallNode("void 0", "<operator>.void", DispatchTypes.STATIC_DISPATCH, binaryNode.getLhs)

            val equalsCallId =
              createEqualsCallNode(accessId, voidCallId, binaryNode.getRhs)

            equalsCallId
          }
          val falseId = {
            val fieldAccessTmpId = createIdentifierNode(localTmpName, binaryNode)

            val indexId = createLiteralNode(index.toString, binaryNode, Some(Defines.Number))

            val accessId =
              createIndexAccessNode(fieldAccessTmpId, indexId, binaryNode)
            accessId
          }
          (lhsId, testId, rhsId, falseId)
        case propertyNode: PropertyNode
            if propertyNode.getValue == null && AstHelpers.getUnaryOperation(
              propertyNode.getKey.tokenType().toString
            ) == "<operator>.spreadObject" =>
          // TODO: how to handle spread objects here?
          logger.debug(s"Using a spread object for object deconstructing is not yet supported! (${code(assignment)})")
          val unknownId = createUnknownNode(propertyNode)
          return unknownId
        case propertyNode: PropertyNode =>
          val valueAsBinaryNode = propertyNode.getValue.asInstanceOf[BinaryNode]
          val lhsId             = valueAsBinaryNode.getLhs.accept(this)
          val rhsId             = valueAsBinaryNode.getRhs.accept(this)
          val testId = {
            val fieldAccessTmpId = createIdentifierNode(localTmpName, propertyNode)

            val keyId = createPropertyKeyNode(propertyNode)

            val accessId =
              createFieldAccessCallNode(fieldAccessTmpId, keyId, propertyNode)

            val voidCallId =
              createCallNode("void 0", "<operator>.void", DispatchTypes.STATIC_DISPATCH, propertyNode.getKey)

            val equalsCallId = createEqualsCallNode(accessId, voidCallId, valueAsBinaryNode.getRhs)
            equalsCallId
          }
          val falseId = {
            val fieldAccessTmpId = createIdentifierNode(localTmpName, propertyNode)

            val keyId = createPropertyKeyNode(propertyNode)

            val accessId =
              createFieldAccessCallNode(fieldAccessTmpId, keyId, propertyNode)
            accessId
          }
          (lhsId, testId, rhsId, falseId)
      }
      val ternaryNodeId =
        createTernaryNode(testId, trueId, falseId, element)

      val assignmentCallId =
        createAssignmentNode(lhsId, ternaryNodeId, element)
      assignmentCallId
    }

    assignment.getLhs match {
      case lhs: ObjectNode =>
        lhs.getElements.asScala.zipWithIndex.foreach {
          case (element: PropertyNode, index: Int) if element.getValue.isInstanceOf[BinaryNode] =>
            val subTreeId = convertDestructingElementWithDefault(element, index)
            addAstEdge(subTreeId, blockId, blockOrder)
            createDependencyNodeForRequire(element.getKeyName, assignment.getRhs)
          case (element: PropertyNode, index: Int) =>
            val subTreeId = convertDestructingElement(element, index)
            addAstEdge(subTreeId, blockId, blockOrder)
            createDependencyNodeForRequire(element.getKeyName, assignment.getRhs)
        }
      case lhs: ArrayLiteralNode =>
        lhs.getElementExpressions.asScala.zipWithIndex.foreach {
          case (element: BinaryNode, index: Int) =>
            val subTreeId = convertDestructingElementWithDefault(element, index)
            addAstEdge(subTreeId, blockId, blockOrder)
          case (element: IdentNode, index: Int) =>
            val subTreeId = convertDestructingElement(element, index)
            addAstEdge(subTreeId, blockId, blockOrder)
            createDependencyNodeForRequire(element.getName, assignment.getRhs)
          // Skipped for array destruction assignment with ignores. The JS parser inserts null here.
          case (null, _) =>
          case (element, _) =>
            logger.debug(s"Destructing the following element is not yet supported: '$element'!")
        }
    }

    val returnTmpId = createIdentifierNode(localTmpName, rhs)
    addAstEdge(returnTmpId, blockId, blockOrder)
    scope.popScope()
    localAstParentStack.pop()
    blockId
  }
  private def convertCommaOp(commaOp: BinaryNode): NewBlock = {
    val lhsId = commaOp.getLhs.accept(this)
    val rhsId = commaOp.getRhs.accept(this)

    // generate the exact same code value that we used to when this was handled through `convertSimpleBinaryOp`
    val code    = codeOf(lhsId) + " , " + codeOf(rhsId)
    val blockId = createBlockNode(commaOp, customCode = Some(code))

    addAstEdge(lhsId, blockId, 0)
    addAstEdge(rhsId, blockId, 1)

    blockId
  }

  private def convertSimpleBinaryOp(binaryNode: BinaryNode): NewCall = {
    val op = AstHelpers.getBinaryOperation(binaryNode.tokenType())

    val lhsId = binaryNode.getLhs.accept(this)
    val rhsId = binaryNode.getRhs.accept(this)

    val callId = createCallNode(
      codeOf(lhsId) + " " + binaryNode.tokenType + " " + codeOf(rhsId),
      op,
      DispatchTypes.STATIC_DISPATCH,
      binaryNode
    )

    addAstEdge(lhsId, callId, 1)
    addArgumentEdge(lhsId, callId, 1)
    addAstEdge(rhsId, callId, 2)
    addArgumentEdge(rhsId, callId, 2)

    callId
  }

  private def createConstructorBlock(unaryNode: UnaryNode): NewBlock = {
    val constructorCall = unaryNode.getExpression.asInstanceOf[CallNode]

    val blockId = createBlockNode(unaryNode)

    scope.pushNewBlockScope(blockId)
    val blockOrder = new OrderTracker()
    localAstParentStack.push(blockId)

    val tmpAllocName =
      PassHelpers.generateUnusedVariableName(usedVariableNames, usedIdentNodes, "_tmp")
    val localTmpAllocId = createLocalNode(tmpAllocName, Defines.Any)
    addLocalToAst(localTmpAllocId)

    val tmpAllocId1 = createIdentifierNode(tmpAllocName, unaryNode)

    val allocId = createCallNode(".alloc", ".alloc", DispatchTypes.STATIC_DISPATCH, unaryNode)

    val assignmentTmpAllocCallId =
      createAssignmentNode(tmpAllocId1, allocId, unaryNode)

    addAstEdge(assignmentTmpAllocCallId, blockId, blockOrder)

    val tmpAllocId2 = createIdentifierNode(tmpAllocName, unaryNode)

    val receiverId = constructorCall.getFunction.accept(this)
    val callId     = handleCallNodeArgs(constructorCall, receiverId, tmpAllocId2, receiverId, None)

    addAstEdge(callId, blockId, blockOrder)

    val tmpAllocReturnId = createIdentifierNode(tmpAllocName, unaryNode)
    addAstEdge(tmpAllocReturnId, blockId, blockOrder)

    scope.popScope()
    localAstParentStack.pop()

    blockId
  }

  private def createUnaryNodeForPrefixOperation(unaryNode: UnaryNode, op: String): NewCall = {
    val astChildId = unaryNode.getExpression.accept(this)

    val code = unaryNode.tokenType().toString + " " + codeOf(astChildId)

    val callId =
      createCallNode(code, op, DispatchTypes.STATIC_DISPATCH, unaryNode)

    addAstEdge(astChildId, callId, 1)
    addArgumentEdge(astChildId, callId, 1)

    callId
  }

  private def createUnaryNode(unaryNode: UnaryNode, op: String): NewCall = {
    val callId     = createCallNode(unaryNode.toString, op, DispatchTypes.STATIC_DISPATCH, unaryNode)
    val astChildId = unaryNode.getExpression.accept(this)

    addAstEdge(astChildId, callId, 1)
    addArgumentEdge(astChildId, callId, 1)

    callId
  }

  override def visit(unaryNode: UnaryNode): NewNode = {
    AstHelpers.getUnaryOperation(unaryNode.tokenType().toString) match {
      case "constructor" => createConstructorBlock(unaryNode)
      case prefixOp if prefixOp == "<operator>.await" =>
        createUnaryNodeForPrefixOperation(unaryNode, prefixOp)
      case op => createUnaryNode(unaryNode, op)
    }
  }

  override def visit(templateLiteralNode: TemplateLiteralNode): NewNode = {
    val args = templateLiteralNode match {
      case node: TemplateLiteralNode.TaggedTemplateLiteralNode =>
        node.getRawStrings.asScala
      case node: TemplateLiteralNode.UntaggedTemplateLiteralNode =>
        node.getExpressions.asScala
    }

    val callId = createCallNode(
      s"__Runtime.TO_STRING(${args.mkString(",")})",
      "__Runtime.TO_STRING",
      DispatchTypes.STATIC_DISPATCH,
      templateLiteralNode
    )

    val callOrder    = new OrderTracker()
    val callArgIndex = new OrderTracker()
    args.foreach { expression =>
      val argId = expression.accept(this)
      addAstEdge(argId, callId, callOrder)
      addArgumentEdge(argId, callId, callArgIndex)

    }
    callId
  }

  override def visit(ternaryNode: TernaryNode): NewNode = {
    createTernaryNode(
      ternaryNode.getTest.accept(this),
      ternaryNode.getTrueExpression.accept(this),
      ternaryNode.getFalseExpression.accept(this),
      ternaryNode
    )
  }

  override def visit(throwNode: ThrowNode): NewNode = {
    val unknownId  = createUnknownNode(throwNode)
    val astChildId = throwNode.getExpression.accept(this)
    addAstEdge(astChildId, unknownId, 1)
    unknownId
  }

  // TODO: Proper handling of with nodes.
  //       The semantic of the with statement can only be
  //       calculated during JS runtime. How to emulate this?
  //       (see: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Statements/with)
  override def visit(withNode: WithNode): NewNode = {
    val unknownId    = createUnknownNode(withNode)
    val expressionId = withNode.getExpression.accept(this)
    addAstEdge(expressionId, unknownId, 1)
    val bodyId = withNode.getBody.accept(this)
    addAstEdge(bodyId, unknownId, 2)
    unknownId
  }

  // TODO: Proper handling of label nodes.
  //       Currently we lack appropriate handling for GOTOs.
  override def visit(labelNode: LabelNode): NewNode = {
    val unknownId  = createUnknownNode(labelNode)
    val astChildId = labelNode.getBody.accept(this)
    addAstEdge(astChildId, unknownId, 1)
    unknownId
  }

  override def visit(catchNode: CatchNode): NewNode = {
    val catchParts = new NewCompositeNode()

    Option(catchNode.getDestructuringPattern).foreach { destructingPattern =>
      val destructingPatternId = destructingPattern.accept(this)
      catchParts.add(destructingPatternId)
    }
    Option(catchNode.getExceptionCondition).foreach { exceptionCondition =>
      val exceptionConditionId = exceptionCondition.accept(this)
      catchParts.add(exceptionConditionId)
    }
    val bodyId = catchNode.getBody.accept(this)
    catchParts.add(bodyId)

    catchParts
  }

  override def visit(tryNode: TryNode): NewNode = {
    val tryNodeId = createControlStructureNode(tryNode, ControlStructureTypes.TRY)

    val bodyId = tryNode.getBody.accept(this)
    addAstEdge(bodyId, tryNodeId, 1)

    val blockId = createBlockNode(tryNode)
    scope.pushNewBlockScope(blockId)
    val blockOrder = new OrderTracker()
    localAstParentStack.push(blockId)

    tryNode.getCatchBlocks.forEach { catchBlock =>
      visitStatements(
        catchBlock.getStatements,
        { statementId =>
          addAstEdge(statementId, blockId, blockOrder)
        }
      )
    }

    addAstEdge(blockId, tryNodeId, 2)
    scope.popScope()
    localAstParentStack.pop()

    Option(tryNode.getFinallyBody).foreach { finallyBody =>
      val finallyBodyId = finallyBody.accept(this)
      addAstEdge(finallyBodyId, tryNodeId, 3)
    }

    tryNodeId
  }

  override def visit(indexNode: IndexNode): NewNode = {
    val baseId  = indexNode.getBase.accept(this)
    val indexId = indexNode.getIndex.accept(this)
    createIndexAccessNode(baseId, indexId, indexNode)
  }

  override def visit(returnNode: ReturnNode): NewNode = {
    val retId = createReturnNode(returnNode)

    Option(returnNode.getExpression).foreach { returnExpression =>
      val retExprId = returnExpression.accept(this)
      addAstEdge(retExprId, retId, 1)
      addArgumentEdge(retExprId, retId, 1)
    }
    retId
  }

  override def visit(errorNode: ErrorNode): NewNode = {
    createUnknownNode(errorNode)
  }

  override def visit(objectNode: ObjectNode): NewNode = {
    val blockId = createBlockNode(objectNode)

    scope.pushNewBlockScope(blockId)
    val blockOrder = new OrderTracker()
    localAstParentStack.push(blockId)

    val tmpName = PassHelpers.generateUnusedVariableName(usedVariableNames, usedIdentNodes, "_tmp")
    val localId = createLocalNode(tmpName, Defines.Any)
    addLocalToAst(localId)

    objectNode.getElements.forEach {
      case element
          if element.getValue == null && AstHelpers.getUnaryOperation(
            element.getKey.tokenType().toString
          ) != "<operator>.spreadObject" => // skip
      case element
          if element.getValue == null && AstHelpers.getUnaryOperation(
            element.getKey.tokenType().toString
          ) == "<operator>.spreadObject" =>
        // TODO: handling of spread objects here
        val exprId = element.getKey.asInstanceOf[UnaryNode].getExpression.accept(this)
        addAstEdge(exprId, blockId, blockOrder)
      case element =>
        val rightHandSideId = element.getValue match {
          case functionNode: FunctionNode =>
            createFunctionNode(functionNode, shouldCreateFunctionReference = true)._1.get
          case other => other.accept(this)
        }

        val leftHandSideTmpId = createIdentifierNode(tmpName, element)

        val keyId = createPropertyKeyNode(element)

        val leftHandSideFieldAccessId =
          createFieldAccessCallNode(leftHandSideTmpId, keyId, element.getKey)

        val assignmentCallId =
          createAssignmentNode(leftHandSideFieldAccessId, rightHandSideId, element)

        addAstEdge(assignmentCallId, blockId, blockOrder)

        // getter + setter:
        Option(element.getGetter).foreach(_.accept(this))
        Option(element.getSetter).foreach(_.accept(this))
    }

    val tmpId = createIdentifierNode(tmpName, objectNode)
    addAstEdge(tmpId, blockId, blockOrder)

    scope.popScope()
    localAstParentStack.pop()

    blockId
  }

  private def createVariableReferenceLinks(): Unit = {
    val resolvedReferenceIt = scope.resolve(createMethodLocalForUnresolvedReference)
    val capturedLocals      = mutable.HashMap.empty[String, NewNode]

    resolvedReferenceIt.foreach { case ResolvedReference(variableNodeId, origin) =>
      var currentScope             = origin.stack
      var currentReferenceId       = origin.referenceNodeId
      var nextReferenceId: NewNode = null

      var done = false
      while (!done) {
        val localOrCapturedLocalIdOption =
          if (currentScope.get.nameToVariableNode.contains(origin.variableName)) {
            done = true
            Some(variableNodeId)
          } else {
            currentScope.flatMap {
              case methodScope: MethodScopeElement =>
                // We have reached a MethodScope and still did not find a local variable to link to.
                // For all non local references the CPG format does not allow us to link
                // directly. Instead we need to create a fake local variable in method
                // scope and link to this local which itself carries the information
                // that it is a captured variable. This needs to be done for each
                // method scope until we reach the originating scope.
                val closureBindingIdProperty =
                  methodScope.methodFullName + ":" + origin.variableName
                capturedLocals
                  .updateWith(closureBindingIdProperty) {
                    case None =>
                      val methodScopeNodeId = methodScope.scopeNode
                      val localId =
                        createLocalNode(origin.variableName, Defines.Any, Some(closureBindingIdProperty))
                      addAstEdge(localId, methodScopeNodeId, 0)
                      val closureBindingId =
                        createClosureBindingNode(closureBindingIdProperty, origin.variableName)

                      methodScope.capturingRefId.foreach(addCaptureEdge(closureBindingId, _))

                      nextReferenceId = closureBindingId

                      Some(localId)
                    case someLocalId =>
                      // When there is already a LOCAL representing the capturing, we do not
                      // need to process the surrounding scope element as this has already
                      // been processed.
                      done = true
                      someLocalId
                  }
              case _: BlockScopeElement => None
            }
          }

        localOrCapturedLocalIdOption.foreach { localOrCapturedLocalId =>
          addRefEdge(localOrCapturedLocalId, currentReferenceId)
          currentReferenceId = nextReferenceId
        }

        currentScope = currentScope.get.surroundingScope
      }
    }
  }

  private def createMethodLocalForUnresolvedReference(
    methodScopeNodeId: NewNode,
    variableName: String
  ): (NewNode, ScopeType) = {
    val varId =
      createLocalNode(variableName, Defines.Any)
    addAstEdge(varId, methodScopeNodeId, 0)
    (varId, MethodScope)
  }

  private def computeScopePath(stack: Option[ScopeElement]): String =
    new ScopeElementIterator(stack)
      .to(Seq)
      .reverse
      .collect { case methodScopeElement: MethodScopeElement =>
        methodScopeElement.name
      }
      .mkString(":")

  private def calcTypeNameAndFullName(classNode: ClassNode): (String, String) = {
    def calcTypeName(classNode: ClassNode): String = {
      val typeName = Option(classNode.getIdent) match {
        case Some(ident) => ident.getName
        // in JS it is possible to create anonymous classes; hence no name
        case None =>
          "_anon_cdecl"
      }
      typeName
    }

    typeToNameAndFullName.get(classNode) match {
      case Some(nameAndFullName) =>
        nameAndFullName
      case None =>
        val name             = calcTypeName(classNode)
        val fullNamePrefix   = source.filePath + ":" + computeScopePath(scope.getScopeHead) + ":"
        val intendedFullName = fullNamePrefix + name
        val postfix          = typeFullNameToPostfix.getOrElse(intendedFullName, 0)

        val resultingFullName =
          if (postfix == 0) {
            intendedFullName
          } else {
            intendedFullName + postfix.toString
          }

        typeFullNameToPostfix.put(intendedFullName, postfix + 1)
        (name, resultingFullName)
    }

  }

  // The first returned string is the method name, second is the full name.
  private def calcMethodNameAndFullName(functionNode: FunctionNode): (String, String) = {
    def calcMethodName(functionNode: FunctionNode): String = {
      val name = functionNode match {
        case _ if functionNode.isAnonymous && functionNode.isProgram =>
          ":program"
        case _ if functionNode.isAnonymous && functionNode.isClassConstructor =>
          "anonClass<constructor>"
        case _ if functionNode.isAnonymous =>
          "anonymous"
        case _ if functionNode.isClassConstructor =>
          s"${functionNode.getName}<constructor>"
        case _ =>
          functionNode.getName
      }

      name
    }

    // functionNode.getName is not necessarily unique and thus the full name calculated based on the scope
    // is not necessarily unique. Specifically we have this problem with lambda functions which are defined
    // in the same scope.
    functionNodeToNameAndFullName.get(functionNode) match {
      case Some(nameAndFullName) =>
        nameAndFullName
      case None =>
        val intendedName   = calcMethodName(functionNode)
        val fullNamePrefix = source.filePath + ":" + computeScopePath(scope.getScopeHead) + ":"
        var name           = intendedName
        var fullName       = ""

        var isUnique = false
        var i        = 1
        while (!isUnique) {
          fullName = fullNamePrefix + name
          if (functionFullNames.contains(fullName)) {
            name = intendedName + i.toString
            i += 1
          } else {
            isUnique = true
          }
        }

        functionFullNames.add(fullName)
        functionNodeToNameAndFullName(functionNode) = (name, fullName)
        (name, fullName)
    }
  }

}

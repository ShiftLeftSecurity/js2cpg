package io.shiftleft.js2cpg.passes

import io.shiftleft.codepropertygraph.generated.*
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.semanticcpg.language.*

class SimpleAstCreationPassTest extends AbstractPassTest {

  "AST generation for simple fragments" should {

    "have correct structure for FILENAME property" in AstFixture("let x = 1;") { cpg =>
      cpg.namespaceBlock.filenameExact("code.js").size shouldBe 1

      val List(program) = cpg.method.nameExact(":program").l
      program.filename shouldBe "code.js"

      val List(typeDecls) = cpg.typeDecl.nameExact(":program").l
      typeDecls.filename shouldBe "code.js"
    }

    "have correct structure for block expression" in AstFixture("let x = (class Foo {}, bar())") { cpg =>
      val List(program) = cpg.method.nameExact(":program").l

      val List(classFooMetaTypeDecl) = cpg.typeDecl.nameExact("Foo<meta>").fullNameExact("code.js::program:Foo<meta>").l
      val List(classFooTypeDecl)     = cpg.typeDecl.nameExact("Foo").fullNameExact("code.js::program:Foo").l

      // constructor
      val List(classFooMethod) = classFooTypeDecl.astChildren.isMethod.nameExact("Foo<constructor>").l
      val List(programBlock)   = program.astChildren.isBlock.l
      val List(assignment)     = programBlock.astChildren.assignment.l

      val List(commaRight) = assignment.astChildren.isBlock.l
      commaRight.astChildren.size shouldBe 2

      val List(refForConstructor) = commaRight.astChildren.isTypeRef.l
      refForConstructor.code shouldBe "class Foo"

      val List(barCall) = commaRight.astChildren.isCall.l
      barCall.code shouldBe "bar()"
    }

    "have correct structure for empty array literal" in AstFixture("var x = []") { cpg =>
      val List(method)      = cpg.method.nameExact(":program").l
      val List(methodBlock) = method.astChildren.isBlock.l

      val List(xAssignment) = methodBlock.astChildren.isCall.l
      xAssignment.name shouldBe Operators.assignment

      val List(arrayCall) = xAssignment.astChildren.isCall.l
      arrayCall.name shouldBe EcmaBuiltins.arrayFactory
      arrayCall.code shouldBe s"${EcmaBuiltins.arrayFactory}()"
      arrayCall.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH
    }

    "have correct structure for array literal with values" in AstFixture("var x = [1, 2]") { cpg =>
      val List(methodBlock) = cpg.method.nameExact(":program").astChildren.isBlock.l

      val List(xAssignment) = methodBlock.astChildren.isCall.l
      xAssignment.name shouldBe Operators.assignment

      val List(pushBlock) = xAssignment.astChildren.isBlock.l

      val List(tmpLocal) = pushBlock.astChildren.isLocal.l
      tmpLocal.name shouldBe "_tmp_0"

      val List(tmpAssignment) = pushBlock.astChildren.isCall.codeExact("_tmp_0 = __ecma.Array.factory()").l
      tmpAssignment.name shouldBe Operators.assignment

      val List(arrayCall) = tmpAssignment.astChildren.isCall.l
      arrayCall.name shouldBe EcmaBuiltins.arrayFactory
      arrayCall.code shouldBe s"${EcmaBuiltins.arrayFactory}()"
      arrayCall.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH

      checkLiterals(pushBlock, 1)
      checkLiterals(pushBlock, 2)

      val List(tmpReturn) = pushBlock.astChildren.isIdentifier.l
      tmpReturn.name shouldBe "_tmp_0"
    }

    "have correct structure for untagged runtime node in call" in AstFixture(s"foo(`Hello $${world}!`)") { cpg =>
      val List(method)      = cpg.method.nameExact(":program").l
      val List(methodBlock) = method.astChildren.isBlock.l

      val List(fooCall) = methodBlock.astChildren.isCall.l
      fooCall.code shouldBe """foo(__Runtime.TO_STRING("Hello ",world,"!"))"""

      val List(templateCall) = fooCall.astChildren.isCall.l
      templateCall.name shouldBe "__Runtime.TO_STRING"
      templateCall.code shouldBe "__Runtime.TO_STRING(\"Hello \",world,\"!\")"

      val List(argument1) = templateCall.astChildren.isLiteral.order(1).l
      argument1.argumentIndex shouldBe 1
      argument1.code shouldBe "\"Hello \""

      val List(argument2) = templateCall.astChildren.isIdentifier.order(2).l
      argument2.argumentIndex shouldBe 2
      argument2.name shouldBe "world"
      argument2.code shouldBe "world"

      val List(argument3) = templateCall.astChildren.isLiteral.order(3).l
      argument3.argumentIndex shouldBe 3
      argument3.code shouldBe "\"!\""
    }

    "have correct structure for untagged runtime node" in AstFixture(s"`$${x + 1}`") { cpg =>
      val List(method)      = cpg.method.nameExact(":program").l
      val List(methodBlock) = method.astChildren.isBlock.l

      val List(call) = methodBlock.astChildren.isCall.l
      call.name shouldBe "__Runtime.TO_STRING"
      call.code shouldBe "__Runtime.TO_STRING(\"\",x + 1,\"\")"

      val List(argument1) = call.astChildren.isLiteral.order(1).l
      argument1.argumentIndex shouldBe 1
      argument1.code shouldBe "\"\""

      val List(argument2) = call.astChildren.isCall.order(2).l
      argument2.argumentIndex shouldBe 2
      argument2.code shouldBe "x + 1"

      val List(argument3) = call.astChildren.isLiteral.order(3).l
      argument3.argumentIndex shouldBe 3
      argument3.code shouldBe "\"\""
    }

    "have correct structure for tagged runtime node" in AstFixture(s"String.raw`../$${42}\\..`") { cpg =>
      val List(method)      = cpg.method.nameExact(":program").l
      val List(methodBlock) = method.astChildren.isBlock.l

      val List(rawCall) = methodBlock.astChildren.isCall.l
      rawCall.code shouldBe "String.raw(" + "__Runtime.TO_STRING" + """("../","\.."), 42)"""

      val List(runtimeCall) = rawCall.astChildren.isCall.nameExact("__Runtime.TO_STRING").l
      runtimeCall.argumentIndex shouldBe 1
      runtimeCall.code shouldBe "__Runtime.TO_STRING" + """("../","\..")"""

      val List(argument1) = runtimeCall.astChildren.isLiteral.codeExact("\"../\"").l
      argument1.order shouldBe 1
      argument1.argumentIndex shouldBe 1

      val List(argument2) =
        runtimeCall.astChildren.isLiteral.codeExact("\"\\..\"").l
      argument2.order shouldBe 2
      argument2.argumentIndex shouldBe 2

      val List(argumentRawCall2) = rawCall.astChildren.isLiteral.codeExact("42").l
      argumentRawCall2.order shouldBe 3
      argumentRawCall2.argumentIndex shouldBe 2
    }

    "have correct structure for try" in AstFixture("""
        |try {
        | open()
        |} catch(err) {
        | handle()
        |} finally {
        | close()
        |}
        |""".stripMargin) { cpg =>
      val List(method)      = cpg.method.nameExact(":program").l
      val List(methodBlock) = method.astChildren.isBlock.l

      val List(tryStatement) = methodBlock.astChildren.isControlStructure.l
      tryStatement.controlStructureType shouldBe ControlStructureTypes.TRY

      val List(tryBlock) = tryStatement.astChildren.isBlock.order(1).l
      tryBlock.ast.isCall.codeExact("open()").size shouldBe 1

      val List(catchBlock) = tryStatement.astChildren.isBlock.order(2).l
      catchBlock.ast.isCall.codeExact("handle()").size shouldBe 1

      val List(finallyBlock) = tryStatement.astChildren.isBlock.order(3).l
      finallyBlock.ast.isCall.codeExact("close()").size shouldBe 1
    }

    "have correct structure for 1 object with simple values" in AstFixture("""
        |var x = {
        | key1: "value",
        | key2: 2,
        | ...rest
        |}
        |""".stripMargin) { cpg =>
      val List(methodBlock) = cpg.method.nameExact(":program").astChildren.isBlock.l
      val List(localX)      = methodBlock.local.nameExact("x").l
      val List(assignment)  = methodBlock.astChildren.isCall.l
      val List(identifierX) = assignment.astChildren.isIdentifier.l

      val List(localXViaRef) = identifierX.refOut.l
      localXViaRef shouldBe localX

      val List(block) = assignment.astChildren.isBlock.l
      checkObjectInitialization(block, ("key1", "\"value\""))
      checkObjectInitialization(block, ("key2", "2"))
    }

    "have correct structure for 1 object with computed values" in AstFixture("""
        |var x = {
        | key1: value(),
        | key2: foo.compute()
        |}
        |""".stripMargin) { cpg =>
      val List(methodBlock) = cpg.method.nameExact(":program").astChildren.isBlock.l
      val List(localX)      = methodBlock.local.nameExact("x").l
      val List(assignment)  = methodBlock.astChildren.isCall.l
      val List(identifierX) = assignment.astChildren.isIdentifier.l

      val List(localXViaRef) = identifierX.refOut.l
      localXViaRef shouldBe localX

      val List(block) = assignment.astChildren.isBlock.l
      checkObjectInitialization(block, ("key1", "value()"))
      checkObjectInitialization(block, ("key2", "foo.compute()"))
    }

    "have correct structure for object with computed property name" ignore AstFixture("""
        |var x = {
        | [ 1 + 1 ]: value()
        |}
        |""".stripMargin) { _ => }

    "have correct structure for conditional expression" in AstFixture("x ? y : z;") { cpg =>
      val List(program) = cpg.method.nameExact(":program").l
      val List(block)   = program.astChildren.isBlock.l
      val List(call)    = block.astChildren.isCall.l
      call.code shouldBe "x ? y : z"
      call.methodFullName shouldBe Operators.conditional

      val List(x, y, z) = call.astChildren.isIdentifier.l
      x.name shouldBe "x"
      y.name shouldBe "y"
      z.name shouldBe "z"
    }

    "have correct name space block for empty file" in AstFixture("") { cpg =>
      val List(file) = cpg.file.l
      file.name should endWith("code.js")

      val List(ns) = cpg.namespaceBlock.l
      ns.name shouldBe Defines.GlobalNamespace
      ns.fullName should endWith(s"code.js:${Defines.GlobalNamespace}")
      ns.order shouldBe 1
      ns.filename shouldBe file.name
    }

    "have :program method correctly attached to files namespace block" in AstFixture("") { cpg =>
      val List(file) = cpg.file.l
      file.name should endWith("code.js")

      val List(ns) = cpg.namespaceBlock.l
      ns.name shouldBe Defines.GlobalNamespace
      ns.fullName should endWith(s"code.js:${Defines.GlobalNamespace}")
      ns.order shouldBe 1
      ns.ast.isMethod.head.name shouldBe ":program"
    }

    "have correct structure for empty method nested in top level method" in AstFixture("function method(x) {}") { cpg =>
      val List(method)       = cpg.method.nameExact(":program").l
      val List(methodMethod) = method.astChildren.isMethod.l

      val List(virtualModifier) = methodMethod.modifier.l
      virtualModifier.modifierType shouldBe ModifierTypes.VIRTUAL

      val List(block) = method.astChildren.isBlock.l

      val List(assignment) = block.astChildren.isCall.l
      assignment.name shouldBe Operators.assignment

      val List(localForMethod) = block.astChildren.isLocal.l
      localForMethod.name shouldBe "method"

      val List(methodIdentifier) = assignment.astChildren.isIdentifier.argumentIndex(1).l
      methodIdentifier.name shouldBe "method"

      methodIdentifier.refOut.head shouldBe localForMethod
    }

    "have correct parameter order in lambda function with ignored param" in AstFixture("var x = ([, param]) => param") {
      cpg =>
        val lambdaFullName    = "code.js::program:anonymous"
        val List(lambda)      = cpg.method.fullNameExact(lambdaFullName).l
        val List(lambdaBlock) = lambda.astChildren.isBlock.l

        val List(param1, param2) = lambda.parameter.l
        param1.order shouldBe 0
        param1.index shouldBe 0
        param1.name shouldBe "this"
        param1.code shouldBe "this"

        param2.order shouldBe 1
        param2.index shouldBe 1
        param2.name shouldBe "param1_0"
        param2.code shouldBe "{param}"

        lambdaBlock.astChildren.isLocal.nameExact("param").size shouldBe 1
        lambdaBlock.astChildren.isCall.codeExact("param = param1_0.param").size shouldBe 1
    }

    "have two lambda functions in same scope level with different full names" in AstFixture(
      """
                                                                                              |var x = (a) => a;
                                                                                              |var y = (b) => b;""".stripMargin
    ) { cpg =>
      val lambda1FullName = "code.js::program:anonymous"
      val lambda2FullName = "code.js::program:anonymous1"

      cpg.method.fullNameExact(lambda1FullName).size shouldBe 1
      cpg.method.fullNameExact(lambda2FullName).size shouldBe 1

      val List(method) = cpg.method.nameExact(":program").l
      val List(block)  = method.astChildren.isBlock.l

      val List(assignment1) = block.astChildren.isCall.order(1).l
      assignment1.name shouldBe Operators.assignment

      val List(lambda1MethodRef) = assignment1.astChildren.isMethodRef.l
      lambda1MethodRef.methodFullName shouldBe lambda1FullName

      val List(assignment2) = block.astChildren.isCall.order(2).l
      assignment2.name shouldBe Operators.assignment

      val List(lambda2MethodRef) = assignment2.astChildren.isMethodRef.l
      lambda2MethodRef.methodFullName shouldBe lambda2FullName
    }

    "be correct for call expression" in AstFixture("""
        |function method(x) {
        |  foo(x);
        |}""".stripMargin) { cpg =>
      val List(method) = cpg.method.nameExact("method").l
      val List(block)  = method.astChildren.isBlock.l

      val List(fooCall) = block.astChildren.isCall.l
      fooCall.code shouldBe "foo(x)"
      fooCall.name shouldBe ""
      fooCall.dispatchType shouldBe DispatchTypes.DYNAMIC_DISPATCH

      val List(receiver) = fooCall.receiver.isIdentifier.l
      receiver.name shouldBe "foo"
      receiver.argumentIndex shouldBe -1

      val List(argumentThis) = fooCall.astChildren.isIdentifier.nameExact("this").l
      argumentThis.argumentIndex shouldBe 0

      val List(argument1) = fooCall.astChildren.isIdentifier.nameExact("x").l
      argument1.argumentIndex shouldBe 1
    }

    "be correct for chained calls" in AstFixture("x.foo(y).bar(z)") { cpg =>
      val List(method) = cpg.method.nameExact(":program").l
      val List(block)  = method.astChildren.isBlock.l

      val List(barCall) = block.astChildren.isCall.l
      barCall.code shouldBe "x.foo(y).bar(z)"
      barCall.name shouldBe ""

      val List(receiver)       = barCall.receiver.isCall.l
      val List(receiverViaAst) = barCall.astChildren.isCall.l

      receiver shouldBe receiverViaAst
      receiver.code shouldBe "(_tmp_0 = x.foo(y)).bar"
      receiver.name shouldBe Operators.fieldAccess
      receiver.argumentIndex shouldBe -1

      val List(barIdentifier) = receiver.astChildren.isFieldIdentifier.l
      barIdentifier.canonicalName shouldBe "bar"
      barIdentifier.argumentIndex shouldBe 2

      val List(tmpAssignment) = receiver.astChildren.isCall.l
      tmpAssignment.code shouldBe "(_tmp_0 = x.foo(y))"
      tmpAssignment.name shouldBe "<operator>.assignment"

      val List(tmpIdentifier) = tmpAssignment.astChildren.isIdentifier.l
      tmpIdentifier.name shouldBe "_tmp_0"
      tmpIdentifier.argumentIndex shouldBe 1

      val List(barBaseTree) = tmpAssignment.astChildren.isCall.l
      barBaseTree.code shouldBe "x.foo(y)"
      barBaseTree.name shouldBe ""
      barBaseTree.argumentIndex shouldBe 2

      // barBaseTree constructs is tested for in another test.

      val List(thisArg) = barCall.astChildren.isIdentifier.argumentIndex(0).l
      thisArg.name shouldBe "_tmp_0"

      val List(zArg) = barCall.astChildren.isIdentifier.argumentIndex(1).l
      zArg.name shouldBe "z"
    }

    "be correct for call on object" in AstFixture("""
                                                    |function method(x) {
                                                    |  x.foo();
                                                    |}
            """.stripMargin) { cpg =>
      val List(method) = cpg.method.nameExact("method").l
      val List(block)  = method.astChildren.isBlock.l

      val List(fooCall) = block.astChildren.isCall.l
      fooCall.code shouldBe "x.foo()"
      fooCall.name shouldBe ""
      fooCall.dispatchType shouldBe DispatchTypes.DYNAMIC_DISPATCH

      val List(receiver) = fooCall.astChildren.isCall.l
      receiver.code shouldBe "x.foo"
      receiver.methodFullName shouldBe Operators.fieldAccess

      val List(base) = receiver.astChildren.isIdentifier.argumentIndex(1).l
      base.name shouldBe "x"

      val List(accessElement) = receiver.astChildren.isFieldIdentifier.argumentIndex(2).l
      accessElement.canonicalName shouldBe "foo"
    }

    "have block for while body for while statement with brackets" in AstFixture("while (x < 0) {}") { cpg =>
      val List(method)    = cpg.method.nameExact(":program").l
      val List(block)     = method.astChildren.isBlock.l
      val List(whileNode) = block.astChildren.isControlStructure.l
      whileNode.controlStructureType shouldBe ControlStructureTypes.WHILE
      whileNode.astChildren.isBlock.size shouldBe 1
    }

    "have no block for while body for while statement without brackets" in AstFixture("""
        |while (x < 0)
        |  x += 1""".stripMargin) { cpg =>
      val List(method) = cpg.method.nameExact(":program").l
      val List(block)  = method.astChildren.isBlock.l

      val List(whileNode) = block.astChildren.isControlStructure.l
      whileNode.controlStructureType shouldBe ControlStructureTypes.WHILE
      whileNode.astChildren.isBlock.size shouldBe 0
    }

    "have local variable for function with correct type full name" in AstFixture("function method(x) {}") { cpg =>
      val List(method) = cpg.method.nameExact(":program").l
      val block        = method.block
      val localFoo     = block.local.head
      localFoo.name shouldBe "method"
      localFoo.typeFullName should endWith("code.js::program:method")
    }

    "have corresponding type decl with correct bindings for function" in AstFixture("function method(x) {}") { cpg =>
      val List(typeDecl) = cpg.typeDecl.nameExact("method").l
      typeDecl.fullName should endWith("code.js::program:method")

      val List(binding) = typeDecl.bindsOut.l
      binding.name shouldBe ""
      binding.signature shouldBe ""

      val List(boundMethod) = binding.refOut.l
      boundMethod shouldBe cpg.method.nameExact("method").head
    }

    "have correct structure for empty method" in AstFixture("function method(x) {}") { cpg =>
      val List(method) = cpg.method.nameExact("method").l
      method.astChildren.isBlock.size shouldBe 1

      val List(thisParam, xParam) = method.parameter.l
      thisParam.order shouldBe 0
      thisParam.index shouldBe 0
      thisParam.name shouldBe "this"
      thisParam.typeFullName shouldBe Defines.Any
      xParam.order shouldBe 1
      xParam.index shouldBe 1
      xParam.name shouldBe "x"
      xParam.typeFullName shouldBe Defines.Any
    }

    "have correct structure for decl assignment" in AstFixture("function foo(x) { var local = 1; }") { cpg =>
      val List(method) = cpg.method.nameExact("foo").l
      val List(block)  = method.astChildren.isBlock.l

      val List(t, x) = method.parameter.l
      t.order shouldBe 0
      t.index shouldBe 0
      t.name shouldBe "this"
      t.typeFullName shouldBe Defines.Any
      x.order shouldBe 1
      x.index shouldBe 1
      x.name shouldBe "x"
      x.typeFullName shouldBe Defines.Any

      val List(local) = block.astChildren.isLocal.l
      local.name shouldBe "local"

      val List(assignmentCall) = block.astChildren.isCall.l
      val List(assignmentOut)  = assignmentCall.astChildren.isIdentifier.l
      assignmentOut.name shouldBe "local"
    }

    "have correct structure for decl assignment with identifier on right hand side" in AstFixture(
      "function foo(x) { var local = x; }"
    ) { cpg =>
      val List(method) = cpg.method.nameExact("foo").l
      val List(block)  = method.astChildren.isBlock.l

      val List(t, x) = method.parameter.l
      t.order shouldBe 0
      t.index shouldBe 0
      t.name shouldBe "this"
      t.typeFullName shouldBe Defines.Any
      x.order shouldBe 1
      x.index shouldBe 1
      x.name shouldBe "x"
      x.typeFullName shouldBe Defines.Any

      val List(local) = block.astChildren.isLocal.l
      local.name shouldBe "local"

      val List(assignmentCall) = block.astChildren.isCall.l
      val List(localVar, xVar) = assignmentCall.astChildren.isIdentifier.l
      localVar.name shouldBe "local"
      xVar.name shouldBe "x"
    }

    "have correct structure for decl assignment of multiple locals" in AstFixture(
      "function foo(x,y) { var local1 = x; var local2 = y; }"
    ) { cpg =>
      val List(method) = cpg.method.nameExact("foo").l
      val List(block)  = method.astChildren.isBlock.l

      val List(t, x, y) = method.parameter.l
      t.order shouldBe 0
      t.index shouldBe 0
      t.name shouldBe "this"
      t.typeFullName shouldBe Defines.Any
      x.order shouldBe 1
      x.index shouldBe 1
      x.name shouldBe "x"
      x.typeFullName shouldBe Defines.Any
      y.order shouldBe 2
      y.index shouldBe 2
      y.name shouldBe "y"
      y.typeFullName shouldBe Defines.Any

      val List(firstLocal, secondLocal) = block.astChildren.isLocal.l
      firstLocal.name shouldBe "local1"
      secondLocal.name shouldBe "local2"

      val List(firstAssigment, secondAssigment) = block.astChildren.isCall.l
      firstAssigment.code shouldBe "local1 = x"
      secondAssigment.code shouldBe "local2 = y"

      val List(outLocal1, outRight1) = firstAssigment.astChildren.isIdentifier.l
      outLocal1.name shouldBe "local1"
      outRight1.name shouldBe "x"

      val List(outLocal2, outRight2) = secondAssigment.astChildren.isIdentifier.l
      outLocal2.name shouldBe "local2"
      outRight2.name shouldBe "y"
    }

    "be correct for nested expression" in AstFixture("function method() { var x; var y; var z; x = y + z; }") { cpg =>
      val List(method)         = cpg.method.nameExact("method").l
      val List(block)          = method.astChildren.isBlock.l
      val List(assignmentCall) = block.astChildren.isCall.l
      val List(identifierX)    = assignmentCall.astChildren.isIdentifier.l
      identifierX.name shouldBe "x"

      val List(plus)                     = assignmentCall.astChildren.isCall.l
      val List(identifierY, identifierZ) = plus.astChildren.isIdentifier.l
      identifierY.name shouldBe "y"
      identifierZ.name shouldBe "z"
    }

    "be correct for while loop" in AstFixture("""
        |function method(x) {
        |  while (x < 1) {
        |    x += 1;
        |  }
        |}""".stripMargin) { cpg =>
      val List(method)      = cpg.method.nameExact("method").l
      val List(methodBlock) = method.astChildren.isBlock.l

      val List(whileNode) = methodBlock.astChildren.isControlStructure.l
      whileNode.controlStructureType shouldBe ControlStructureTypes.WHILE
      whileNode.order shouldBe 1

      val List(whileCondition) = whileNode.astChildren.isCall.l
      whileCondition.code shouldBe "x < 1"
      whileCondition.order shouldBe 1

      val List(whileBlock) = whileNode.astChildren.isBlock.l
      whileBlock.order shouldBe 2

      val List(assign) = whileBlock.astChildren.isCall.l
      assign.code shouldBe "x += 1"
      assign.order shouldBe 1

      val List(identifierX) = assign.astChildren.isIdentifier.l
      identifierX.code shouldBe "x"
      identifierX.order shouldBe 1

      val List(literal1) = assign.astChildren.isLiteral.l
      literal1.code shouldBe "1"
      literal1.order shouldBe 2
    }

    "be correct for if" in AstFixture("""
        |function method(x) {
        |  var y;
        |  if (x > 0)
        |    y = 0;
        |}""".stripMargin) { cpg =>
      val List(method)      = cpg.method.nameExact("method").l
      val List(methodBlock) = method.astChildren.isBlock.l
      methodBlock.astChildren.isLocal.size shouldBe 1

      val List(ifNode) = methodBlock.astChildren.isControlStructure.l
      ifNode.controlStructureType shouldBe ControlStructureTypes.IF
      ifNode.order shouldBe 1

      val List(ifCondition) = ifNode.astChildren.isCall.order(1).l
      ifCondition.code shouldBe "x > 0"

      val List(assignment) = ifNode.astChildren.isCall.order(2).l
      assignment.code shouldBe "y = 0"

      val List(identifierY) = assignment.astChildren.isIdentifier.l
      identifierY.code shouldBe "y"
      identifierY.order shouldBe 1

      val List(literal0) = assignment.astChildren.isLiteral.l
      literal0.code shouldBe "0"
      literal0.order shouldBe 2
    }

    "be correct for if-else" in AstFixture("""
        |function method(x) {
        |  var y;
        |  if (x > 0) {
        |    y = 0;
        |  } else {
        |    y = 1;
        |  }
        |}""".stripMargin) { cpg =>
      val List(method)      = cpg.method.nameExact("method").l
      val List(methodBlock) = method.astChildren.isBlock.l
      val List(localY)      = methodBlock.astChildren.isLocal.l
      localY.order shouldBe 0

      val List(ifNode) = methodBlock.astChildren.isControlStructure.l
      ifNode.controlStructureType shouldBe ControlStructureTypes.IF
      ifNode.order shouldBe 1

      val List(ifCondition) = ifNode.astChildren.isCall.l
      ifCondition.code shouldBe "x > 0"
      ifCondition.order shouldBe 1

      val List(ifBlock)       = ifNode.astChildren.isBlock.order(2).l
      val List(ifBlockAssign) = ifBlock.astChildren.isCall.l
      ifBlockAssign.code shouldBe "y = 0"
      ifBlockAssign.order shouldBe 1

      val List(elseBlock)       = ifNode.astChildren.isBlock.order(3).l
      val List(elseBlockAssign) = elseBlock.astChildren.isCall.l
      elseBlockAssign.code shouldBe "y = 1"
      elseBlockAssign.order shouldBe 1
    }

    "be correct for for-loop with for-in" in AstFixture("""
        |for (var i in arr) {
        |  foo(i)
        |}""".stripMargin) { cpg =>
      val List(method)      = cpg.method.nameExact(":program").l
      val List(methodBlock) = method.astChildren.isBlock.l
      val List(loopBlock)   = methodBlock.astChildren.isBlock.l
      checkForInOrOf(loopBlock)
    }

    "be correct for for-loop with for-of" in AstFixture("""
        |for (var i of arr) {
        |  foo(i)
        |}""".stripMargin) { cpg =>
      val List(method)      = cpg.method.nameExact(":program").l
      val List(methodBlock) = method.astChildren.isBlock.l
      val List(loopBlock)   = methodBlock.astChildren.isBlock.l
      checkForInOrOf(loopBlock)
    }

    "be correct for for-loop with empty test" in AstFixture("for(;;){}") { cpg =>
      val List(method)      = cpg.method.nameExact(":program").l
      val List(methodBlock) = method.astChildren.isBlock.l
      val List(forNode)     = methodBlock.astChildren.isControlStructure.l
      forNode.controlStructureType shouldBe ControlStructureTypes.FOR
      forNode.order shouldBe 1

      val List(forCondition) = forNode.astChildren.isLiteral.order(2).l
      forCondition.code shouldBe "true"
    }

    "be correct for for-loop" in AstFixture("""
        |function method(x,y) {
        |  for (x = 0; x < 1; x += 1) {
        |    z = 0;
        |  }
        |}""".stripMargin) { cpg =>
      val List(method) = cpg.method.nameExact("method").l

      val List(parameterInX) = method.parameter.order(1).l
      parameterInX.index shouldBe 1
      parameterInX.name shouldBe "x"

      val List(parameterInY) = method.parameter.order(2).l
      parameterInY.index shouldBe 2
      parameterInY.name shouldBe "y"

      val List(methodBlock) = method.astChildren.isBlock.l

      val List(forNode) = methodBlock.astChildren.isControlStructure.l
      forNode.controlStructureType shouldBe ControlStructureTypes.FOR
      forNode.order shouldBe 1

      val List(forInit) = forNode.astChildren.isCall.order(1).l
      forInit.code shouldBe "x = 0"

      val List(forCondition) = forNode.astChildren.isCall.order(2).l
      forCondition.code shouldBe "x < 1"

      val List(forModify) = forNode.astChildren.isCall.order(3).l
      forModify.code shouldBe "x += 1"

      val List(forBlock) = forNode.astChildren.isBlock.l
      forBlock.order shouldBe 4

      val List(forBlockAssign) = forBlock.astChildren.isCall.l
      forBlockAssign.code shouldBe "z = 0"
      forBlockAssign.order shouldBe 1
    }

    "handle switch statements and" should {
      "be correct for switch with one case" in AstFixture("switch (x) { case 1: y; }") { cpg =>
        val List(program)      = cpg.method.nameExact(":program").l
        val List(programBlock) = program.astChildren.isBlock.l

        val List(switch) = programBlock.astChildren.isControlStructure.l
        switch.controlStructureType shouldBe ControlStructureTypes.SWITCH

        val List(switchExpr) = switch.astChildren.isIdentifier.nameExact("x").l
        switchExpr.order shouldBe 1
        switchExpr.code shouldBe "x"

        val List(switchBlock) = switch.astChildren.isBlock.l
        val List(caseLabel)   = switchBlock._jumpTargetViaAstOut.codeExact("case 1:").l
        caseLabel.order shouldBe 1
        val List(caseExpr) = switchBlock.astChildren.isLiteral.codeExact("1").l
        caseExpr.order shouldBe 2
        val List(identifierY) = switchBlock.astChildren.isIdentifier.codeExact("y").l
        identifierY.order shouldBe 3
      }

      "be correct for switch with multiple cases" in AstFixture("switch (x) { case 1: y; case 2: z; }") { cpg =>
        val List(program)      = cpg.method.nameExact(":program").l
        val List(programBlock) = program.astChildren.isBlock.l

        val List(switch) = programBlock.astChildren.isControlStructure.l
        switch.controlStructureType shouldBe ControlStructureTypes.SWITCH
        val List(switchExpr) = switch.astChildren.isIdentifier.nameExact("x").l
        switchExpr.order shouldBe 1
        switchExpr.code shouldBe "x"

        val List(switchBlock) = switch.astChildren.isBlock.l
        val List(caseLabel1)  = switchBlock._jumpTargetViaAstOut.codeExact("case 1:").l
        caseLabel1.order shouldBe 1

        val List(caseExpr1) = switchBlock.astChildren.isLiteral.codeExact("1").l
        caseExpr1.order shouldBe 2

        val List(identifierY) = switchBlock.astChildren.isIdentifier.codeExact("y").l
        identifierY.order shouldBe 3

        val List(caseLabel2) = switchBlock._jumpTargetViaAstOut.codeExact("case 2:").l
        caseLabel2.order shouldBe 4

        val List(caseExpr2) = switchBlock.astChildren.isLiteral.codeExact("2").l
        caseExpr2.order shouldBe 5

        val List(identifierZ) = switchBlock.astChildren.isIdentifier.codeExact("z").l
        identifierZ.order shouldBe 6
      }

      "be correct for switch with multiple cases on same spot" in AstFixture("switch (x) { case 1: case 2: y; }") {
        cpg =>
          val List(program)      = cpg.method.nameExact(":program").l
          val List(programBlock) = program.astChildren.isBlock.l

          val List(switch) = programBlock.astChildren.isControlStructure.l
          switch.controlStructureType shouldBe ControlStructureTypes.SWITCH

          val List(switchExpr) = switch.astChildren.isIdentifier.nameExact("x").l
          switchExpr.order shouldBe 1
          switchExpr.code shouldBe "x"

          val List(switchBlock) = switch.astChildren.isBlock.l
          val List(caseLabel1)  = switchBlock._jumpTargetViaAstOut.codeExact("case 1:").l
          caseLabel1.order shouldBe 1

          val List(caseExpr1) = switchBlock.astChildren.isLiteral.codeExact("1").l
          caseExpr1.order shouldBe 2

          val List(caseLabel2) = switchBlock._jumpTargetViaAstOut.codeExact("case 2:").l
          caseLabel2.order shouldBe 3

          val List(caseExpr2) = switchBlock.astChildren.isLiteral.codeExact("2").l
          caseExpr2.order shouldBe 4

          val List(identifierY) = switchBlock.astChildren.isIdentifier.codeExact("y").l
          identifierY.order shouldBe 5
      }

      "be correct for switch with multiple cases and multiple cases on same spot" in AstFixture(
        "switch (x) { case 1: case 2: y; case 3: z; }"
      ) { cpg =>
        val List(program)      = cpg.method.nameExact(":program").l
        val List(programBlock) = program.astChildren.isBlock.l

        val List(switch) = programBlock.astChildren.isControlStructure.l
        switch.controlStructureType shouldBe ControlStructureTypes.SWITCH

        val List(switchExpr) = switch.astChildren.isIdentifier.nameExact("x").l
        switchExpr.order shouldBe 1
        switchExpr.code shouldBe "x"

        val List(switchBlock) = switch.astChildren.isBlock.l
        val List(caseLabel1)  = switchBlock._jumpTargetViaAstOut.codeExact("case 1:").l
        caseLabel1.order shouldBe 1

        val List(caseExpr1) = switchBlock.astChildren.isLiteral.codeExact("1").l
        caseExpr1.order shouldBe 2

        val List(caseLabel2) =
          switchBlock._jumpTargetViaAstOut.codeExact("case 2:").l
        caseLabel2.order shouldBe 3

        val List(caseExpr2) = switchBlock.astChildren.isLiteral.codeExact("2").l
        caseExpr2.order shouldBe 4

        val List(identifierY) =
          switchBlock.astChildren.isIdentifier.codeExact("y").l
        identifierY.order shouldBe 5

        val List(caseLabel3) =
          switchBlock._jumpTargetViaAstOut.codeExact("case 3:").l
        caseLabel3.order shouldBe 6

        val List(caseExpr3) = switchBlock.astChildren.isLiteral.codeExact("3").l
        caseExpr3.order shouldBe 7

        val List(identifierZ) =
          switchBlock.astChildren.isIdentifier.codeExact("z").l
        identifierZ.order shouldBe 8
      }

      "be correct for switch with default case" in AstFixture("switch (x) { default: y; }") { cpg =>
        val List(program)      = cpg.method.nameExact(":program").l
        val List(programBlock) = program.astChildren.isBlock.l

        val List(switch) = programBlock.astChildren.isControlStructure.l
        switch.controlStructureType shouldBe ControlStructureTypes.SWITCH

        val List(switchExpr) = switch.astChildren.isIdentifier.nameExact("x").l
        switchExpr.order shouldBe 1
        switchExpr.code shouldBe "x"

        programBlock.astChildren.isLiteral.size shouldBe 0

        val List(switchBlock) = switch.astChildren.isBlock.l
        val List(caseLabel)   = switchBlock._jumpTargetViaAstOut.codeExact("default:").l
        caseLabel.order shouldBe 1

        val List(identifierY) = switchBlock.astChildren.isIdentifier.nameExact("y").l
        identifierY.order shouldBe 2
      }

      "be correct for switch with case and default combined" in AstFixture(
        "switch (x) { case 1: y; break; default: z; }"
      ) { cpg =>
        val List(program)      = cpg.method.nameExact(":program").l
        val List(programBlock) = program.astChildren.isBlock.l

        val List(switch) = programBlock.astChildren.isControlStructure.l
        switch.controlStructureType shouldBe ControlStructureTypes.SWITCH

        val List(switchExpr) = switch.astChildren.isIdentifier.nameExact("x").l
        switchExpr.order shouldBe 1
        switchExpr.code shouldBe "x"

        val List(switchBlock) = switch.astChildren.isBlock.l
        val List(caseLabel1)  = switchBlock._jumpTargetViaAstOut.codeExact("case 1:").l
        caseLabel1.order shouldBe 1

        val List(caseExpr1) = switchBlock.astChildren.isLiteral.codeExact("1").l
        caseExpr1.order shouldBe 2

        val List(identifierY) = switchBlock.astChildren.isIdentifier.nameExact("y").l
        identifierY.order shouldBe 3

        val List(break) =
          switchBlock.astChildren.isControlStructure.controlStructureTypeExact(ControlStructureTypes.BREAK).l
        break.order shouldBe 4

        val List(caseLabel2) = switchBlock._jumpTargetViaAstOut.codeExact("default:").l
        caseLabel2.order shouldBe 5

        val List(identifierZ) = switchBlock.astChildren.isIdentifier.nameExact("z").l
        identifierZ.order shouldBe 6
      }

      "be correct for switch with nested switch" in AstFixture("switch (x) { default: switch(y) { default: z; } }") {
        cpg =>
          val List(program)      = cpg.method.nameExact(":program").l
          val List(programBlock) = program.astChildren.isBlock.l

          val List(topLevelSwitch) = programBlock.astChildren.isControlStructure.l
          topLevelSwitch.controlStructureType shouldBe ControlStructureTypes.SWITCH

          val List(topLevelSwitchExpr) = topLevelSwitch.astChildren.isIdentifier.nameExact("x").l
          topLevelSwitchExpr.order shouldBe 1
          topLevelSwitchExpr.code shouldBe "x"

          val List(topLevelSwitchBlock) = topLevelSwitch.astChildren.isBlock.l

          val List(topLevelCaseLabel) = topLevelSwitchBlock._jumpTargetViaAstOut.codeExact("default:").l
          topLevelCaseLabel.order shouldBe 1

          val List(nestedSwitch) = topLevelSwitchBlock.astChildren.isControlStructure.l
          nestedSwitch.controlStructureType shouldBe ControlStructureTypes.SWITCH

          val List(nestedSwitchExpr) = nestedSwitch.astChildren.isIdentifier.nameExact("y").l
          nestedSwitchExpr.order shouldBe 1
          nestedSwitchExpr.code shouldBe "y"

          val List(nestedSwitchBlock) = nestedSwitch.astChildren.isBlock.l
          val List(nestedCaseLabel)   = nestedSwitchBlock._jumpTargetViaAstOut.codeExact("default:").l
          nestedCaseLabel.order shouldBe 1

          val List(identifierZ) = nestedSwitchBlock.astChildren.isIdentifier.nameExact("z").l
          identifierZ.order shouldBe 2
      }
    }

    "be correct for unary expression '++'" in AstFixture("""
        |function method(x) {
        |  ++x;
        |}""".stripMargin) { cpg =>
      val List(method)        = cpg.method.nameExact("method").l
      val List(methodBlock)   = method.astChildren.isBlock.l
      val List(unaryPlusCall) = methodBlock.astChildren.isCall.l
      unaryPlusCall.code shouldBe "++x"
      val List(identifierX) = unaryPlusCall.astChildren.isIdentifier.l
      identifierX.name shouldBe "x"
    }

    "be correct for member access used in an assignment (direct)" in AstFixture("""
        |function method(x) {
        |  z = x.a;
        |}""".stripMargin) { cpg =>
      val List(method)      = cpg.method.nameExact("method").l
      val List(methodBlock) = method.astChildren.isBlock.l
      val List(assignment)  = methodBlock.astChildren.isCall.l
      assignment.methodFullName shouldBe Operators.assignment

      val List(identifierZ) = assignment.astChildren.isIdentifier.l
      identifierZ.name shouldBe "z"

      val List(rightHandSide) = assignment.astChildren.isCall.l
      rightHandSide.methodFullName shouldBe Operators.fieldAccess

      val List(identifierRightX) = rightHandSide.astChildren.isIdentifier.argumentIndex(1).l
      identifierRightX.name shouldBe "x"
      identifierRightX.code shouldBe "x"

      val List(identifierRightA) = rightHandSide.astChildren.isFieldIdentifier.argumentIndex(2).l
      identifierRightA.canonicalName shouldBe "a"
      identifierRightA.code shouldBe "a"
    }

    "be correct for member access used in an assignment (chained)" in AstFixture("""
        |function method(x) {
        |  z = x.a.b.c;
        |}""".stripMargin) { cpg =>
      val List(method)      = cpg.method.nameExact("method").l
      val List(methodBlock) = method.astChildren.isBlock.l
      val List(assignment)  = methodBlock.astChildren.isCall.l
      assignment.methodFullName shouldBe Operators.assignment

      val List(identifierZ) = assignment.astChildren.isIdentifier.l
      identifierZ.name shouldBe "z"

      val List(rightC) = assignment.astChildren.isCall.l
      rightC.methodFullName shouldBe Operators.fieldAccess

      val List(identifierC) = rightC.astChildren.isFieldIdentifier.l
      identifierC.canonicalName shouldBe "c"

      val List(rightB) = rightC.astChildren.isCall.l
      rightB.methodFullName shouldBe Operators.fieldAccess

      val List(identifierB) = rightB.astChildren.isFieldIdentifier.l
      identifierB.canonicalName shouldBe "b"

      val List(rightA) = rightB.astChildren.isCall.l
      rightA.methodFullName shouldBe Operators.fieldAccess

      val List(identifierX) = rightA.astChildren.isIdentifier.argumentIndex(1).l
      identifierX.name shouldBe "x"

      val List(identifierA) = rightA.astChildren.isFieldIdentifier.argumentIndex(2).l
      identifierA.canonicalName shouldBe "a"
    }

    "be correct for member access used in an assignment (chained with method call)" in AstFixture("""
        |function method(x) {
        |  z = x.a.b.c();
        |}""".stripMargin) { cpg =>
      val List(method)      = cpg.method.nameExact("method").l
      val List(methodBlock) = method.astChildren.isBlock.l
      val List(assignment)  = methodBlock.astChildren.isCall.l
      assignment.methodFullName shouldBe Operators.assignment

      val List(identifierZ) = assignment.astChildren.isIdentifier.l
      identifierZ.name shouldBe "z"

      val List(right) = assignment.astChildren.isCall.l

      val List(callToC) = right.astChildren.isCall.l
      callToC.methodFullName shouldBe Operators.fieldAccess

      val List(identifierC) = callToC.astChildren.isFieldIdentifier.l
      identifierC.canonicalName shouldBe "c"

      val List(assignmentToTmp) = callToC.astChildren.isCall.l
      assignmentToTmp.methodFullName shouldBe Operators.assignment

      val List(tmpIdentifier) = assignmentToTmp.astChildren.isIdentifier.l
      tmpIdentifier.name shouldBe "_tmp_0"

      val List(fieldAccessXAB) = assignmentToTmp.astChildren.isCall.l
      fieldAccessXAB.methodFullName shouldBe Operators.fieldAccess

      val List(identifierB) = fieldAccessXAB.astChildren.isFieldIdentifier.l
      identifierB.canonicalName shouldBe "b"

      val List(callToA) = fieldAccessXAB.astChildren.isCall.l
      callToA.methodFullName shouldBe Operators.fieldAccess

      val List(identifierX) = callToA.astChildren.isIdentifier.argumentIndex(1).l
      identifierX.name shouldBe "x"

      val List(identifierA) = callToA.astChildren.isFieldIdentifier.argumentIndex(2).l
      identifierA.canonicalName shouldBe "a"
    }

    "be correct for member access used as return" in AstFixture("""
                                                                  |function method(x) {
                                                                  |  return x.a;
                                                                  |}""".stripMargin) { cpg =>
      val List(method)          = cpg.method.nameExact("method").l
      val List(methodBlock)     = method.astChildren.isBlock.l
      val List(returnStatement) = methodBlock.astChildren.isReturn.l
      val List(rightHandSide)   = returnStatement.astChildren.isCall.l
      rightHandSide.order shouldBe 1
      rightHandSide.argumentIndex shouldBe 1
      rightHandSide.methodFullName shouldBe Operators.fieldAccess

      val List(identifierX) = rightHandSide.astChildren.isIdentifier.argumentIndex(1).l
      identifierX.name shouldBe "x"

      val List(identifierA) = rightHandSide.astChildren.isFieldIdentifier.argumentIndex(2).l
      identifierA.canonicalName shouldBe "a"
    }

    "be correct for member access as useless statement" in AstFixture("""
        |function method(x) {
        |  x.a;
        |}""".stripMargin) { cpg =>
      val List(method)      = cpg.method.nameExact("method").l
      val List(methodBlock) = method.astChildren.isBlock.l
      val List(statement)   = methodBlock.astChildren.isCall.l
      statement.methodFullName shouldBe Operators.fieldAccess

      val List(identifierX) = statement.astChildren.isIdentifier.argumentIndex(1).l
      identifierX.name shouldBe "x"

      val List(identifierA) = statement.astChildren.isFieldIdentifier.argumentIndex(2).l
      identifierA.canonicalName shouldBe "a"
    }

    "be correct for empty method" in AstFixture("function method() {}") { cpg =>
      val List(program) = cpg.method.nameExact("method").l
      program.astChildren.isBlock.size shouldBe 1
      val blockMethodReturn = program.methodReturn
      blockMethodReturn.code shouldBe "RET"
    }

  }

  private def checkObjectInitialization(node: Block, member: (String, String)): Unit = {
    val (keyName, assignedValue) = member

    val List(localTmp) = node.astChildren.isLocal.nameExact("_tmp_0").l
    localTmp.order shouldBe 0

    val List(tmp) = node.astChildren.isIdentifier.nameExact("_tmp_0").l
    tmp.code shouldBe "_tmp_0"

    val List(call) = node.astChildren.isCall.codeExact(s"_tmp_0.$keyName = $assignedValue").l
    call.methodFullName shouldBe Operators.assignment

    val List(tmpAccess) = call.argument(1).start.isCall.l
    tmpAccess.code shouldBe s"_tmp_0.$keyName"
    tmpAccess.methodFullName shouldBe Operators.fieldAccess
    tmpAccess.argumentIndex shouldBe 1
    val List(value) = call.argument(2).start.l
    value.code shouldBe assignedValue

    val List(leftHandSideTmpId) = tmpAccess.astChildren.isIdentifier.nameExact("_tmp_0").l
    leftHandSideTmpId.code shouldBe "_tmp_0"

    val List(key) = tmpAccess.astChildren.isFieldIdentifier.l
    key.canonicalName shouldBe keyName
  }

  private def checkForInOrOf(node: Block): Unit = {
    val List(localIterator) = node.astChildren.isLocal.nameExact("_iterator_0").l
    val List(localResult)   = node.astChildren.isLocal.nameExact("_result_0").l
    val List(localI)        = node.astChildren.isLocal.nameExact("i").l
    val List(iteratorAssignment) =
      node.astChildren.isCall.codeExact("_iterator_0 = Object.keys(arr)[Symbol.iterator]()").l
    iteratorAssignment.name shouldBe Operators.assignment

    val List(iteratorAssignmentLhs) = iteratorAssignment.astChildren.isIdentifier.l
    iteratorAssignmentLhs.name shouldBe "_iterator_0"
    iteratorAssignmentLhs.order shouldBe 1
    iteratorAssignmentLhs.argumentIndex shouldBe 1

    val List(iteratorAssignmentRhs) = iteratorAssignment.astChildren.isCall.l
    iteratorAssignmentRhs.code shouldBe "Object.keys(arr)[Symbol.iterator]()"
    iteratorAssignmentRhs.order shouldBe 2
    iteratorAssignmentRhs.argumentIndex shouldBe 2
    iteratorAssignmentRhs.dispatchType shouldBe DispatchTypes.DYNAMIC_DISPATCH

    val List(varResult) = node.astChildren.isIdentifier.nameExact("_result_0").l
    varResult.code shouldBe "_result_0"

    val List(varI) = node.astChildren.isIdentifier.nameExact("i").l
    varI.code shouldBe "i"

    val List(loop) = node.astChildren.isControlStructure.l
    loop.controlStructureType shouldBe ControlStructureTypes.WHILE

    val List(loopTestCall) = loop.astChildren.isCall.codeExact("!(_result_0 = _iterator_0.next()).done").l
    loopTestCall.name shouldBe Operators.not
    loopTestCall.order shouldBe 1

    val List(doneMaCall) = loopTestCall.astChildren.isCall.codeExact("(_result_0 = _iterator_0.next()).done").l
    doneMaCall.name shouldBe Operators.fieldAccess

    val List(doneMaBase) = doneMaCall.astChildren.isCall.codeExact("(_result_0 = _iterator_0.next())").l
    doneMaBase.name shouldBe Operators.assignment
    doneMaBase.order shouldBe 1
    doneMaBase.argumentIndex shouldBe 1

    val List(doneMaBaseLhs) = doneMaBase.astChildren.isIdentifier.order(1).l
    doneMaBaseLhs.name shouldBe "_result_0"
    doneMaBaseLhs.argumentIndex shouldBe 1

    val List(doneMaBaseRhs) = doneMaBase.astChildren.isCall.order(2).l
    doneMaBaseRhs.code shouldBe "_iterator_0.next()"
    doneMaBaseRhs.argumentIndex shouldBe 2

    val List(doneMember) = doneMaCall.astChildren.isFieldIdentifier.canonicalNameExact("done").l
    doneMember.order shouldBe 2
    doneMember.argumentIndex shouldBe 2

    val List(whileLoopBlock) = loop.astChildren.isBlock.l
    whileLoopBlock.order shouldBe 2

    val List(loopVarAssignmentCall) = whileLoopBlock.astChildren.isCall.codeExact("i = _result_0.value").l
    loopVarAssignmentCall.name shouldBe Operators.assignment
    loopVarAssignmentCall.order shouldBe 1

    val List(fooCall) = whileLoopBlock.astChildren.isBlock.astChildren.isCall.codeExact("foo(i)").l
    fooCall.name shouldBe ""
  }

  private def checkLiterals(node: Block, element: Int): Unit = {
    val List(pushCall) = node.astChildren.isCall.codeExact(s"_tmp_0.push($element)").l

    val List(pushCallReceiver) = pushCall.receiver.isCall.l
    pushCallReceiver.name shouldBe Operators.fieldAccess
    pushCallReceiver.argumentIndex shouldBe -1

    val pushCallReceiverBase = pushCallReceiver.argument(1).asInstanceOf[Identifier]
    pushCallReceiverBase.name shouldBe "_tmp_0"

    val pushCallReceiverMember = pushCallReceiver.argument(2).asInstanceOf[FieldIdentifier]
    pushCallReceiverMember.canonicalName shouldBe "push"

    val pushCallThis = pushCall.argument(0).asInstanceOf[Identifier]
    pushCallThis.name shouldBe "_tmp_0"

    val pushCallArg = pushCall.argument(1).asInstanceOf[Literal]
    pushCallArg.code shouldBe element.toString
  }

}

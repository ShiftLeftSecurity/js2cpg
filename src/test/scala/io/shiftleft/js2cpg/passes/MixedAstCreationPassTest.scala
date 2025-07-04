package io.shiftleft.js2cpg.passes

import io.shiftleft.codepropertygraph.generated.*
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.semanticcpg.language.*

class MixedAstCreationPassTest extends AbstractPassTest {

  "AST method full names" should {
    "anonymous arrow function full name 1" in AstFixture("var func = (x) => x") { cpg =>
      cpg.method.fullName.toSetMutable should contain("code.js::program:anonymous")
    }
    "anonymous arrow function full name 2" in AstFixture("this.func = (x) => x") { cpg =>
      cpg.method.fullName.toSetMutable should contain("code.js::program:anonymous")
    }
    "anonymous function expression full name 1" in AstFixture("var func = function (x) {x}") { cpg =>
      cpg.method.fullName.toSetMutable should contain("code.js::program:anonymous")
    }
    "anonymous function expression full name 2" in AstFixture("this.func = function (x) {x}") { cpg =>
      cpg.method.fullName.toSetMutable should contain("code.js::program:anonymous")
    }
    "anonymous constructor full name 1" in AstFixture("class X { constructor(){} }") { cpg =>
      cpg.method.fullName.toSetMutable should contain("code.js::program:X<constructor>")
    }
    "anonymous constructor of anonymous class full name" in AstFixture("""
        |var x = class {
        |  constructor(y) {
        |}
        |}""".stripMargin) { cpg =>
      cpg.method.fullName.toSetMutable should contain("code.js::program:anonClass<constructor>")
    }
  }

  "AST variable scoping and linking" should {
    "have correct references for single local var" in AstFixture("""
        |var x;
        |x = 1;""".stripMargin) { cpg =>
      val List(method)       = cpg.method.nameExact(":program").l
      val List(methodBlock)  = method.astChildren.isBlock.l
      val List(localX)       = methodBlock.astChildren.isLocal.l
      val List(assignment)   = methodBlock.astChildren.isCall.l
      val List(identifierX)  = assignment.astChildren.isIdentifier.l
      val List(localXViaRef) = identifierX.refOut.l
      localXViaRef shouldBe localX
    }

    "have correct references for single local let" in AstFixture("""
        |let x;
        |x = 1;""".stripMargin) { cpg =>
      val List(method)       = cpg.method.nameExact(":program").l
      val List(methodBlock)  = method.astChildren.isBlock.l
      val List(localX)       = methodBlock.astChildren.isLocal.l
      val List(assignment)   = methodBlock.astChildren.isCall.l
      val List(identifierX)  = assignment.astChildren.isIdentifier.l
      val List(localXViaRef) = identifierX.refOut.l
      localXViaRef shouldBe localX
    }

    "have correct references for undeclared local" in AstFixture("x = 1;") { cpg =>
      val List(method)       = cpg.method.nameExact(":program").l
      val List(methodBlock)  = method.astChildren.isBlock.l
      val List(localX)       = methodBlock.astChildren.isLocal.l
      val List(assignment)   = methodBlock.astChildren.isCall.l
      val List(identifierX)  = assignment.astChildren.isIdentifier.l
      val List(localXViaRef) = identifierX.refOut.l
      localXViaRef shouldBe localX
    }

    "have correct references for undeclared local with 2 refs" in AstFixture("""
        |x = 1;
        |x = 2;""".stripMargin) { cpg =>
      val List(method)        = cpg.method.nameExact(":program").l
      val List(methodBlock)   = method.astChildren.isBlock.l
      val List(localX)        = methodBlock.astChildren.isLocal.l
      val List(assignment1)   = methodBlock.astChildren.isCall.order(1).l
      val List(identifierX1)  = assignment1.astChildren.isIdentifier.l
      val List(localXViaRef1) = identifierX1.refOut.l
      localXViaRef1 shouldBe localX

      val List(assignment2)   = methodBlock.astChildren.isCall.order(2).l
      val List(identifierX2)  = assignment2.astChildren.isIdentifier.l
      val List(localXViaRef2) = identifierX2.refOut.l
      localXViaRef2 shouldBe localX
    }

    "have correct references for undeclared local in block" in AstFixture("{ x = 1; }") { cpg =>
      val List(method)       = cpg.method.nameExact(":program").l
      val List(methodBlock)  = method.astChildren.isBlock.l
      val List(localX)       = methodBlock.astChildren.isLocal.l
      val List(nestedBlock)  = methodBlock.astChildren.isBlock.l
      val List(assignment)   = nestedBlock.astChildren.isCall.l
      val List(identifierX)  = assignment.astChildren.isIdentifier.l
      val List(localXViaRef) = identifierX.refOut.l
      localXViaRef shouldBe localX
    }

    "have correct references for single var in block" in AstFixture("""
        |{ var x; }
        |x = 1;""".stripMargin) { cpg =>
      val List(method)       = cpg.method.nameExact(":program").l
      val List(methodBlock)  = method.astChildren.isBlock.l
      val List(nestedBlock)  = methodBlock.astChildren.isBlock.l
      val List(localX)       = nestedBlock.astChildren.isLocal.l
      val List(assignment)   = methodBlock.astChildren.isCall.l
      val List(identifierX)  = assignment.astChildren.isIdentifier.l
      val List(localXViaRef) = identifierX.refOut.l
      localXViaRef shouldBe localX
    }

    "have correct references for single post declared var" in AstFixture("""
        |x = 1;
        |var x;""".stripMargin) { cpg =>
      val List(method)       = cpg.method.nameExact(":program").l
      val List(methodBlock)  = method.astChildren.isBlock.l
      val List(localX)       = methodBlock.astChildren.isLocal.l
      val List(assignment)   = methodBlock.astChildren.isCall.l
      val List(identifierX)  = assignment.astChildren.isIdentifier.l
      val List(localXViaRef) = identifierX.refOut.l
      localXViaRef shouldBe localX
    }

    "have correct references for single post declared var in block" in AstFixture("""
        |x = 1;
        |{ var x; }""".stripMargin) { cpg =>
      val List(method)       = cpg.method.nameExact(":program").l
      val List(methodBlock)  = method.astChildren.isBlock.l
      val List(nestedBlock)  = methodBlock.astChildren.isBlock.l
      val List(localX)       = nestedBlock.astChildren.isLocal.l
      val List(assignment)   = methodBlock.astChildren.isCall.l
      val List(identifierX)  = assignment.astChildren.isIdentifier.l
      val List(localXViaRef) = identifierX.refOut.l
      localXViaRef shouldBe localX
    }

    "have correct references for single nested access to let" in AstFixture("""
        |let x;
        |{ x = 1; }""".stripMargin) { cpg =>
      val List(method)       = cpg.method.nameExact(":program").l
      val List(methodBlock)  = method.astChildren.isBlock.l
      val List(localX)       = methodBlock.astChildren.isLocal.l
      val List(nestedBlock)  = methodBlock.astChildren.isBlock.l
      val List(assignment)   = nestedBlock.astChildren.isCall.l
      val List(identifierX)  = assignment.astChildren.isIdentifier.l
      val List(localXViaRef) = identifierX.refOut.l
      localXViaRef shouldBe localX
    }

    "have correct references for shadowing let" in AstFixture("""
        |let x;
        |{
        |  let x;
        |  x = 1;
        |}
        |x = 1;""".stripMargin) { cpg =>
      val List(method)            = cpg.method.nameExact(":program").l
      val List(methodBlock)       = method.astChildren.isBlock.l
      val List(outerLocalX)       = methodBlock.astChildren.isLocal.l
      val List(nestedBlock)       = methodBlock.astChildren.isBlock.l
      val List(innerLocalX)       = nestedBlock.astChildren.isLocal.l
      val List(innerAssignment)   = nestedBlock.astChildren.isCall.l
      val List(innerIdentifierX)  = innerAssignment.astChildren.isIdentifier.l
      val List(innerLocalXViaRef) = innerIdentifierX.refOut.l
      innerLocalXViaRef shouldBe innerLocalX

      val List(outerAssignment)   = methodBlock.astChildren.isCall.l
      val List(outerIdentifierX)  = outerAssignment.astChildren.isIdentifier.l
      val List(outerLocalXViaRef) = outerIdentifierX.refOut.l
      outerLocalXViaRef shouldBe outerLocalX
    }

    "have correct closure binding (destructing parameter)" in AstFixture("""
        |const WindowOpen = ({ value }) => {
        |  return () => windowOpenButton(value);
        |};""".stripMargin) { cpg =>
      cpg.local.name("value").closureBindingId.l shouldBe List("code.js::program:anonymous:anonymous:value")
    }

    "have correct closure binding (single variable)" in AstFixture("""
        |function foo() {
        |  x = 1;
        |  function bar() {
        |    x = 2;
        |  }
        |}""".stripMargin) { cpg =>
      val List(fooMethod)      = cpg.method.nameExact("foo").l
      val List(fooBlock)       = fooMethod.astChildren.isBlock.l
      val List(fooLocalX)      = fooBlock.astChildren.isLocal.nameExact("x").l
      val List(barRef)         = fooBlock.astChildren.isCall.astChildren.isMethodRef.l
      val List(closureBinding) = barRef.captureOut.l
      closureBinding.closureBindingId shouldBe Option("code.js::program:foo:bar:x")
      closureBinding.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE

      closureBinding.refOut.l shouldBe List(fooLocalX)

      val List(barMethod)      = cpg.method.nameExact("bar").l
      val List(barMethodBlock) = barMethod.astChildren.isBlock.l
      val List(barLocals)      = barMethodBlock.astChildren.isLocal.l
      barLocals.closureBindingId shouldBe Option("code.js::program:foo:bar:x")

      val List(identifierX) = barMethodBlock.astChildren.isCall.astChildren.isIdentifier.nameExact("x").l
      identifierX.refOut.l shouldBe List(barLocals)
    }

    "have correct closure binding (two variables)" in AstFixture("""
        |function foo() {
        |  x = 1;
        |  y = 1;
        |  function bar() {
        |    x = 2;
        |    y = 2;
        |  }
        |}""".stripMargin) { cpg =>
      val List(fooMethod) = cpg.method.nameExact("foo").l
      val List(fooBlock)  = fooMethod.astChildren.isBlock.l
      val List(fooLocalX) = fooBlock.astChildren.isLocal.nameExact("x").l
      val List(fooLocalY) = fooBlock.astChildren.isLocal.nameExact("y").l
      val List(barRef)    = fooBlock.astChildren.isCall.astChildren.isMethodRef.l

      val List(closureBindForY, closureBindForX) = barRef.captureOut.l

      closureBindForX.closureBindingId shouldBe Option("code.js::program:foo:bar:x")
      closureBindForX.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      closureBindForX.refOut.l shouldBe List(fooLocalX)

      closureBindForY.closureBindingId shouldBe Option("code.js::program:foo:bar:y")
      closureBindForY.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      closureBindForY.refOut.l shouldBe List(fooLocalY)

      val List(barMethod)                    = cpg.method.nameExact("bar").l
      val List(barMethodBlock)               = barMethod.astChildren.isBlock.l
      val List(barLocalsForY, barLocalsForX) = barMethodBlock.astChildren.isLocal.l

      barLocalsForX.name shouldBe "x"
      barLocalsForX.closureBindingId shouldBe Option("code.js::program:foo:bar:x")

      val List(identifierX) = barMethodBlock.astChildren.isCall.astChildren.isIdentifier.nameExact("x").l
      identifierX.refOut.l shouldBe List(barLocalsForX)

      barLocalsForY.name shouldBe "y"
      barLocalsForY.closureBindingId shouldBe Option("code.js::program:foo:bar:y")

      val List(identifierY) = barMethodBlock.astChildren.isCall.astChildren.isIdentifier.nameExact("y").l
      identifierY.refOut.l shouldBe List(barLocalsForY)
    }

    "have correct closure binding for capturing over 2 levels" in AstFixture("""
        |function foo() {
        |  x = 1;
        |  function bar() {
        |    x = 2;
        |    function baz() {
        |      x = 3;
        |    }
        |  }
        |}""".stripMargin) { cpg =>
      val List(fooMethod) = cpg.method.nameExact("foo").l
      val List(fooBlock)  = fooMethod.astChildren.isBlock.l
      val List(fooLocalX) = fooBlock.astChildren.isLocal.nameExact("x").l
      val List(barRef)    = fooBlock.astChildren.isCall.astChildren.isMethodRef.l

      val List(closureBindingXInFoo) = barRef.captureOut.l
      closureBindingXInFoo.closureBindingId shouldBe Option("code.js::program:foo:bar:x")
      closureBindingXInFoo.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      closureBindingXInFoo.refOut.l shouldBe List(fooLocalX)

      val List(barMethod)      = cpg.method.nameExact("bar").l
      val List(barMethodBlock) = barMethod.astChildren.isBlock.l

      val List(barLocalX) = barMethodBlock.astChildren.isLocal.nameExact("x").l
      barLocalX.closureBindingId shouldBe Option("code.js::program:foo:bar:x")

      val List(barIdentifierX) = barMethodBlock.astChildren.isCall.astChildren.isIdentifier.nameExact("x").l
      barIdentifierX.refOut.l shouldBe List(barLocalX)

      val List(bazRef)               = barMethodBlock.astChildren.isCall.astChildren.isMethodRef.l
      val List(closureBindingXInBar) = bazRef.captureOut.l
      closureBindingXInBar.closureBindingId shouldBe Option("code.js::program:foo:bar:baz:x")
      closureBindingXInBar.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      closureBindingXInBar.refOut.l shouldBe List(barLocalX)

      val List(bazMethod)      = cpg.method.nameExact("baz").l
      val List(bazMethodBlock) = bazMethod.astChildren.isBlock.l
      val List(bazLocalX)      = bazMethodBlock.astChildren.isLocal.nameExact("x").l
      bazLocalX.closureBindingId shouldBe Option("code.js::program:foo:bar:baz:x")

      val List(bazIdentifierX) = bazMethodBlock.astChildren.isCall.astChildren.isIdentifier.nameExact("x").l
      bazIdentifierX.refOut.l shouldBe List(bazLocalX)
    }

    "have correct closure binding for capturing over 2 levels with intermediate blocks" in AstFixture("""
        |function foo() {
        |  x = 1;
        |  function bar() {
        |    x = 2;
        |    {
        |      function baz() {
        |        {
        |          x = 3;
        |        }
        |      }
        |    }
        |  }
        |}""".stripMargin) { cpg =>
      val List(fooMethod)            = cpg.method.nameExact("foo").l
      val List(fooBlock)             = fooMethod.astChildren.isBlock.l
      val List(fooLocalX)            = fooBlock.astChildren.isLocal.nameExact("x").l
      val List(barRef)               = fooBlock.astChildren.isCall.astChildren.isMethodRef.l
      val List(closureBindingXInFoo) = barRef.captureOut.l
      closureBindingXInFoo.closureBindingId shouldBe Option("code.js::program:foo:bar:x")
      closureBindingXInFoo.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      closureBindingXInFoo.refOut.l shouldBe List(fooLocalX)

      val List(barMethod)      = cpg.method.nameExact("bar").l
      val List(barMethodBlock) = barMethod.astChildren.isBlock.l

      val List(barLocalX) = barMethodBlock.astChildren.isLocal.nameExact("x").l
      barLocalX.closureBindingId shouldBe Option("code.js::program:foo:bar:x")

      val List(barIdentifierX) = barMethodBlock.astChildren.isCall.astChildren.isIdentifier.nameExact("x").l
      barIdentifierX.refOut.l shouldBe List(barLocalX)

      val List(barMethodInnerBlock)  = barMethodBlock.astChildren.isBlock.l
      val List(bazRef)               = barMethodInnerBlock.astChildren.isCall.astChildren.isMethodRef.l
      val List(closureBindingXInBar) = bazRef.captureOut.l
      closureBindingXInBar.closureBindingId shouldBe Option("code.js::program:foo:bar:baz:x")
      closureBindingXInBar.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      closureBindingXInBar.refOut.l shouldBe List(barLocalX)

      val List(bazMethod)      = cpg.method.nameExact("baz").l
      val List(bazMethodBlock) = bazMethod.astChildren.isBlock.l

      val List(bazLocalX) = bazMethodBlock.astChildren.isLocal.nameExact("x").l
      bazLocalX.closureBindingId shouldBe Option("code.js::program:foo:bar:baz:x")

      val List(bazMethodInnerBlock) = bazMethodBlock.astChildren.isBlock.l
      val List(bazIdentifierX)      = bazMethodInnerBlock.astChildren.isCall.astChildren.isIdentifier.nameExact("x").l
      bazIdentifierX.refOut.l shouldBe List(bazLocalX)
    }

    "have correct closure binding for capturing over 2 levels with no intermediate use" in AstFixture("""
        |function foo() {
        |  x = 1;
        |  function bar() {
        |    function baz() {
        |      x = 3;
        |    }
        |  }
        |}""".stripMargin) { cpg =>
      val List(fooMethod)            = cpg.method.nameExact("foo").l
      val List(fooBlock)             = fooMethod.astChildren.isBlock.l
      val List(fooLocalX)            = fooBlock.astChildren.isLocal.nameExact("x").l
      val List(barRef)               = fooBlock.astChildren.isCall.astChildren.isMethodRef.l
      val List(closureBindingXInFoo) = barRef.captureOut.l
      closureBindingXInFoo.closureBindingId shouldBe Option("code.js::program:foo:bar:x")
      closureBindingXInFoo.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      closureBindingXInFoo.refOut.l shouldBe List(fooLocalX)

      val List(barMethod)      = cpg.method.nameExact("bar").l
      val List(barMethodBlock) = barMethod.astChildren.isBlock.l

      val List(barLocalX) = barMethodBlock.astChildren.isLocal.nameExact("x").l
      barLocalX.closureBindingId shouldBe Option("code.js::program:foo:bar:x")

      val List(bazRef)               = barMethodBlock.astChildren.isCall.astChildren.isMethodRef.l
      val List(closureBindingXInBar) = bazRef.captureOut.l
      closureBindingXInBar.closureBindingId shouldBe Option("code.js::program:foo:bar:baz:x")
      closureBindingXInBar.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      closureBindingXInBar.refOut.l shouldBe List(barLocalX)

      val List(bazMethod)      = cpg.method.nameExact("baz").l
      val List(bazMethodBlock) = bazMethod.astChildren.isBlock.l

      val List(bazLocalX) = bazMethodBlock.astChildren.isLocal.nameExact("x").l
      bazLocalX.closureBindingId shouldBe Option("code.js::program:foo:bar:baz:x")

      val List(bazIdentifierX) = bazMethodBlock.astChildren.isCall.astChildren.isIdentifier.nameExact("x").l
      bazIdentifierX.refOut.l shouldBe List(bazLocalX)
    }

    "have correct closure binding for capturing the same variable into 2 different anonymous methods" in AstFixture("""
        |function foo() {
        |  var x = 1;
        |  var anon1 = y => 2*x;
        |  var anon2 = y => 2*x;
        |}""".stripMargin) { cpg =>
      val List(fooMethod) = cpg.method.nameExact("foo").l
      val List(fooBlock)  = fooMethod.astChildren.isBlock.l
      val List(fooLocalX) = fooBlock.astChildren.isLocal.nameExact("x").l

      val List(anon1Ref) =
        fooBlock.astChildren.isCall.astChildren.isMethodRef.methodFullNameExact("code.js::program:foo:anonymous").l

      val List(closureBindingXAnon1) = anon1Ref.captureOut.l
      closureBindingXAnon1.closureBindingId shouldBe Option("code.js::program:foo:anonymous:x")
      closureBindingXAnon1.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      closureBindingXAnon1.refOut.l shouldBe List(fooLocalX)

      val List(anon2Ref) =
        fooBlock.astChildren.isCall.astChildren.isMethodRef.methodFullNameExact("code.js::program:foo:anonymous1").l
      val List(closureBindingXAnon2) = anon2Ref.captureOut.l
      closureBindingXAnon2.closureBindingId shouldBe Option("code.js::program:foo:anonymous1:x")
      closureBindingXAnon2.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      closureBindingXAnon2.refOut.l shouldBe List(fooLocalX)
    }

    "have correct closure bindings" in AstFixture("""
        |function foo() {
        |  x = 1;
        |  function bar() {
        |    x = 2;
        |  }
        |}""".stripMargin) { cpg =>
      val List(fooMethod)      = cpg.method.nameExact("foo").l
      val List(fooBlock)       = fooMethod.astChildren.isBlock.l
      val List(fooLocalX)      = fooBlock.astChildren.isLocal.nameExact("x").l
      val List(barRef)         = fooBlock.astChildren.isCall.astChildren.isMethodRef.l
      val List(closureBinding) = barRef.captureOut.l
      closureBinding.closureBindingId shouldBe Option("code.js::program:foo:bar:x")
      closureBinding.evaluationStrategy shouldBe EvaluationStrategies.BY_REFERENCE
      closureBinding.refOut.l shouldBe List(fooLocalX)

      val List(barMethod)      = cpg.method.nameExact("bar").l
      val List(barMethodBlock) = barMethod.astChildren.isBlock.l
      val List(barLocals)      = barMethodBlock.astChildren.isLocal.l
      barLocals.closureBindingId shouldBe Option("code.js::program:foo:bar:x")

      val List(identifierX) = barMethodBlock.astChildren.isCall.astChildren.isIdentifier.nameExact("x").l
      identifierX.refOut.l shouldBe List(barLocals)
    }

    "have correct method full names for scoped anonymous functions" in AstFixture("""
        |var anon1 = x => {
        |  var anon2 = y => {};
        |}
        |var anon3 = x => {
        |  var anon4 = y => {};
        |}""".stripMargin) { cpg =>
      cpg.method.lineNumber(2).head.fullName shouldBe "code.js::program:anonymous"
      cpg.method.lineNumber(3).head.fullName shouldBe "code.js::program:anonymous:anonymous"
      cpg.method.lineNumber(5).head.fullName shouldBe "code.js::program:anonymous1"
      cpg.method.lineNumber(6).head.fullName shouldBe "code.js::program:anonymous1:anonymous"
    }
  }

  "AST generation for mixed fragments" should {
    "simple js fragment with call" in AstFixture("""
        |function source(a) { return a; }
        |var l = source(3);""".stripMargin) { cpg =>
      val List(program)      = cpg.method.nameExact(":program").l
      val List(method)       = cpg.method.nameExact("source").l
      val List(programBlock) = program.astChildren.isBlock.l
      val List(methodBlock)  = method.astChildren.isBlock.l
      method.parameter.size shouldBe 2

      val List(localSource, localL) = programBlock.astChildren.isLocal.l
      localSource.name shouldBe "source"
      localSource.typeFullName shouldBe "code.js::program:source"
      localL.name shouldBe "l"

      val List(callToSource) = programBlock.astChildren.isCall.codeExact("l = source(3)").l
      val List(identifierL)  = callToSource.astChildren.isIdentifier.l
      identifierL.name shouldBe "l"

      val List(call) = callToSource.astChildren.isCall.l
      call.astChildren.isLiteral.codeExact("3").size shouldBe 1

      val List(returnFromMethod) = methodBlock.astChildren.isReturn.l
      returnFromMethod.astChildren.isIdentifier.nameExact("a").size shouldBe 1
    }

    "simple js fragment with array access" in AstFixture("result = rows[0].solution;") { cpg =>
      val List(program)      = cpg.method.nameExact(":program").l
      val List(programBlock) = program.astChildren.isBlock.l
      val List(call)         = programBlock.astChildren.isCall.l
      val List(rowsCall)     = call.astChildren.isCall.l
      rowsCall.astChildren.isFieldIdentifier.canonicalNameExact("solution").size shouldBe 1

      val List(rowsCallLeft) = rowsCall.astChildren.isCall.l
      rowsCallLeft.astChildren.isLiteral.codeExact("0").size shouldBe 1
      rowsCallLeft.astChildren.isIdentifier.nameExact("rows").size shouldBe 1
      call.astChildren.isIdentifier.nameExact("result").size shouldBe 1
    }
  }

  "AST generation for destructing assignment" should {
    "have correct structure for object destruction assignment with declaration" in AstFixture("var {a, b} = x;") {
      cpg =>
        val List(program)      = cpg.method.nameExact(":program").l
        val List(programBlock) = program.astChildren.isBlock.l

        val List(localA) = cpg.local.nameExact("a").l
        val List(localB) = cpg.local.nameExact("b").l
        localA.referencingIdentifiers.name.head shouldBe "a"
        localB.referencingIdentifiers.name.head shouldBe "b"

        val List(destructionBlock) = programBlock.astChildren.isBlock.l
        destructionBlock.code shouldBe "{a, b} = x"
        destructionBlock.astChildren.isCall.codeExact("_tmp_0 = x").size shouldBe 1

        val List(localTmp) = destructionBlock.astChildren.isLocal.nameExact("_tmp_0").l

        val List(assignmentToA) = destructionBlock.astChildren.isCall.codeExact("a = _tmp_0.a").l
        assignmentToA.astChildren.isIdentifier.size shouldBe 1

        val List(fieldAccessA) = assignmentToA.astChildren.isCall.codeExact("_tmp_0.a").l
        fieldAccessA.name shouldBe Operators.fieldAccess
        fieldAccessA.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
        fieldAccessA.astChildren.isFieldIdentifier.canonicalNameExact("a").size shouldBe 1

        val List(assignmentToB) = destructionBlock.astChildren.isCall.codeExact("b = _tmp_0.b").l
        assignmentToB.astChildren.isIdentifier.size shouldBe 1

        val List(fieldAccessB) = assignmentToB.astChildren.isCall.codeExact("_tmp_0.b").l
        fieldAccessB.name shouldBe Operators.fieldAccess
        fieldAccessB.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
        fieldAccessB.astChildren.isFieldIdentifier.canonicalNameExact("b").size shouldBe 1

        val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
        tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for object destruction assignment with declaration and ternary init" in AstFixture(
      "const { a, b } = test() ? foo() : bar();"
    ) { cpg =>
      val List(program)      = cpg.method.nameExact(":program").l
      val List(programBlock) = program.astChildren.isBlock.l
      cpg.local.nameExact("a").size shouldBe 1
      cpg.local.nameExact("b").size shouldBe 1

      val List(destructionBlock) = programBlock.astChildren.isBlock.l
      destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
      destructionBlock.astChildren.isCall.codeExact("_tmp_0 = test() ? foo() : bar()").size shouldBe 1

      val List(assignmentToA) = destructionBlock.astChildren.isCall.codeExact("a = _tmp_0.a").l
      assignmentToA.astChildren.isIdentifier.size shouldBe 1

      val List(fieldAccessA) = assignmentToA.astChildren.isCall.codeExact("_tmp_0.a").l
      fieldAccessA.name shouldBe Operators.fieldAccess
      fieldAccessA.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      fieldAccessA.astChildren.isFieldIdentifier.canonicalNameExact("a").size shouldBe 1

      val List(assignmentToB) = destructionBlock.astChildren.isCall.codeExact("b = _tmp_0.b").l
      assignmentToB.astChildren.isIdentifier.size shouldBe 1

      val List(fieldAccessB) = assignmentToB.astChildren.isCall.codeExact("_tmp_0.b").l
      fieldAccessB.name shouldBe Operators.fieldAccess
      fieldAccessB.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      fieldAccessB.astChildren.isFieldIdentifier.canonicalNameExact("b").size shouldBe 1

      val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
      tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for object destruction assignment without declaration" in AstFixture("({a, b} = x);") {
      cpg =>
        val List(program)      = cpg.method.nameExact(":program").l
        val List(programBlock) = program.astChildren.isBlock.l
        cpg.local.nameExact("a").size shouldBe 1
        cpg.local.nameExact("b").size shouldBe 1

        val List(destructionBlock) = programBlock.astChildren.isBlock.l
        destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
        destructionBlock.astChildren.isCall.codeExact("_tmp_0 = x").size shouldBe 1

        val List(assignmentToA) = destructionBlock.astChildren.isCall.codeExact("a = _tmp_0.a").l
        assignmentToA.astChildren.isIdentifier.size shouldBe 1

        val List(fieldAccessA) = assignmentToA.astChildren.isCall.codeExact("_tmp_0.a").l
        fieldAccessA.name shouldBe Operators.fieldAccess
        fieldAccessA.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
        fieldAccessA.astChildren.isFieldIdentifier.canonicalNameExact("a").size shouldBe 1

        val List(assignmentToB) = destructionBlock.astChildren.isCall.codeExact("b = _tmp_0.b").l
        assignmentToB.astChildren.isIdentifier.size shouldBe 1

        val List(fieldAccessB) = assignmentToB.astChildren.isCall.codeExact("_tmp_0.b").l
        fieldAccessB.name shouldBe Operators.fieldAccess
        fieldAccessB.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
        fieldAccessB.astChildren.isFieldIdentifier.canonicalNameExact("b").size shouldBe 1

        val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
        tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for object destruction assignment with defaults" in AstFixture("var {a = 1, b = 2} = x;") {
      cpg =>
        val List(program)      = cpg.method.nameExact(":program").l
        val List(programBlock) = program.astChildren.isBlock.l
        programBlock.astChildren.isLocal.nameExact("a").size shouldBe 1
        programBlock.astChildren.isLocal.nameExact("b").size shouldBe 1

        val List(destructionBlock) = programBlock.astChildren.isBlock.l
        destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
        destructionBlock.astChildren.isCall.codeExact("_tmp_0 = x").size shouldBe 1

        val List(assignmentToA) = destructionBlock.assignment.codeExact("a = _tmp_0.a === void 0 ? 1 : _tmp_0.a").l
        assignmentToA.astChildren.isIdentifier.size shouldBe 1

        val List(ifA) = assignmentToA.astChildren.isCall.codeExact("_tmp_0.a === void 0 ? 1 : _tmp_0.a").l
        ifA.name shouldBe Operators.conditional

        val List(testA) = ifA.astChildren.isCall.codeExact("_tmp_0.a === void 0").l
        testA.name shouldBe Operators.equals

        val List(testAFieldAccess) = testA.astChildren.isCall.codeExact("_tmp_0.a").l
        testAFieldAccess.name shouldBe Operators.fieldAccess

        testA.astChildren.isCall.codeExact("void 0").size shouldBe 1

        ifA.astChildren.isLiteral.codeExact("1").size shouldBe 1

        val List(falseBranchA) = ifA.astChildren.isCall.codeExact("_tmp_0.a").l
        falseBranchA.name shouldBe Operators.fieldAccess

        val List(assignmentToB) = destructionBlock.assignment.codeExact("b = _tmp_0.b === void 0 ? 2 : _tmp_0.b").l
        assignmentToB.astChildren.isIdentifier.size shouldBe 1

        val List(ifB) = assignmentToB.astChildren.isCall.codeExact("_tmp_0.b === void 0 ? 2 : _tmp_0.b").l
        ifB.name shouldBe Operators.conditional

        val List(testB) = ifB.astChildren.isCall.codeExact("_tmp_0.b === void 0").l
        testB.name shouldBe Operators.equals

        val List(testBFieldAccess) = testB.astChildren.isCall.codeExact("_tmp_0.b").l
        testBFieldAccess.name shouldBe Operators.fieldAccess

        testB.astChildren.isCall.codeExact("void 0").size shouldBe 1

        ifB.astChildren.isLiteral.codeExact("2").size shouldBe 1

        val List(falseBranchB) = ifB.astChildren.isCall.codeExact("_tmp_0.b").l
        falseBranchB.name shouldBe Operators.fieldAccess

        val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
        tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for object destruction assignment with reassignment" in AstFixture(
      "var {a: n, b: m} = x;"
    ) { cpg =>
      val List(program)      = cpg.method.nameExact(":program").l
      val List(programBlock) = program.astChildren.isBlock.l
      cpg.local.nameExact("n").size shouldBe 1
      cpg.local.nameExact("m").size shouldBe 1

      val List(destructionBlock) = programBlock.astChildren.isBlock.l
      destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
      destructionBlock.astChildren.isCall.codeExact("_tmp_0 = x").size shouldBe 1

      val List(assignmentToN) = destructionBlock.astChildren.isCall.codeExact("n = _tmp_0.a").l
      assignmentToN.astChildren.isIdentifier.size shouldBe 1

      val List(fieldAccessN) = assignmentToN.astChildren.isCall.codeExact("_tmp_0.a").l
      fieldAccessN.name shouldBe Operators.fieldAccess
      fieldAccessN.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      fieldAccessN.astChildren.isFieldIdentifier.canonicalNameExact("a").size shouldBe 1

      val List(assignmentToM) = destructionBlock.astChildren.isCall.codeExact("m = _tmp_0.b").l
      assignmentToM.astChildren.isIdentifier.size shouldBe 1

      val List(fieldAccessM) = assignmentToM.astChildren.isCall.codeExact("_tmp_0.b").l
      fieldAccessM.name shouldBe Operators.fieldAccess
      fieldAccessM.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      fieldAccessM.astChildren.isFieldIdentifier.canonicalNameExact("b").size shouldBe 1

      val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
      tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for object destruction assignment with reassignment and defaults" in AstFixture(
      "var {a: n = 1, b: m = 2} = x;"
    ) { cpg =>
      val List(program)      = cpg.method.nameExact(":program").l
      val List(programBlock) = program.astChildren.isBlock.l
      programBlock.astChildren.isLocal.nameExact("n").size shouldBe 1
      programBlock.astChildren.isLocal.nameExact("m").size shouldBe 1

      val List(destructionBlock) = programBlock.astChildren.isBlock.l
      destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
      destructionBlock.astChildren.isCall.codeExact("_tmp_0 = x").size shouldBe 1

      val List(assignmentToN) = destructionBlock.assignment.codeExact("n = _tmp_0.a === void 0 ? 1 : _tmp_0.a").l
      assignmentToN.astChildren.isIdentifier.size shouldBe 1

      val List(ifA) = assignmentToN.astChildren.isCall.codeExact("_tmp_0.a === void 0 ? 1 : _tmp_0.a").l
      ifA.name shouldBe Operators.conditional

      val List(testA) = ifA.astChildren.isCall.codeExact("_tmp_0.a === void 0").l
      testA.name shouldBe Operators.equals

      val List(testAFieldAccess) = testA.astChildren.isCall.codeExact("_tmp_0.a").l
      testAFieldAccess.name shouldBe Operators.fieldAccess

      testA.astChildren.isCall.codeExact("void 0").size shouldBe 1

      ifA.astChildren.isLiteral.codeExact("1").size shouldBe 1

      val List(falseBranchA) = ifA.astChildren.isCall.codeExact("_tmp_0.a").l
      falseBranchA.name shouldBe Operators.fieldAccess

      val List(assignmentToM) = destructionBlock.assignment.codeExact("m = _tmp_0.b === void 0 ? 2 : _tmp_0.b").l
      assignmentToN.astChildren.isIdentifier.size shouldBe 1

      val List(ifB) = assignmentToM.astChildren.isCall.codeExact("_tmp_0.b === void 0 ? 2 : _tmp_0.b").l
      ifB.name shouldBe Operators.conditional

      val List(testB) = ifB.astChildren.isCall.codeExact("_tmp_0.b === void 0").l
      testB.name shouldBe Operators.equals

      val List(testBFieldAccess) = testB.astChildren.isCall.codeExact("_tmp_0.b").l
      testBFieldAccess.name shouldBe Operators.fieldAccess

      testB.astChildren.isCall.codeExact("void 0").size shouldBe 1

      ifB.astChildren.isLiteral.codeExact("2").size shouldBe 1

      val List(falseBranchB) = ifB.astChildren.isCall.codeExact("_tmp_0.b").l
      falseBranchB.name shouldBe Operators.fieldAccess

      val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
      tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct ref edge (destructing parameter)" in AstFixture("""
        |const WindowOpen = ({ value }) => {
        |  return () => windowOpenButton(value);
        |};""".stripMargin) { cpg =>
      val List(param) = cpg.identifier.name("param1_0").refsTo.collectAll[MethodParameterIn].l
      param.name shouldBe "param1_0"
      param.code shouldBe "{value}"
      param.method.fullName shouldBe "code.js::program:anonymous"
    }

    "have correct structure for object deconstruction in function parameter" in AstFixture(
      "function foo({ a }, b) {};"
    ) { cpg =>
      val List(program)   = cpg.method.nameExact(":program").l
      val List(fooMethod) = program.astChildren.isMethod.nameExact("foo").l
      val List(a)         = fooMethod.parameter.nameExact("param1_0").l
      a.code shouldBe "{a}"
      a.order shouldBe 1
      a.index shouldBe 1
      val List(b) = fooMethod.parameter.nameExact("b").l
      b.code shouldBe "b"
      b.order shouldBe 2
      b.index shouldBe 2
    }

    "have correct structure for object destruction assignment in call argument" in AstFixture("foo({a, b} = x);") {
      cpg =>
        val List(program)      = cpg.method.nameExact(":program").l
        val List(programBlock) = program.astChildren.isBlock.l
        cpg.local.nameExact("a").size shouldBe 1
        cpg.local.nameExact("b").size shouldBe 1

        val List(fooCall)          = programBlock.astChildren.isCall.l
        val List(destructionBlock) = fooCall.astChildren.isBlock.l
        destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
        destructionBlock.astChildren.isCall.codeExact("_tmp_0 = x").size shouldBe 1

        val List(assignmentToA) = destructionBlock.astChildren.isCall.codeExact("a = _tmp_0.a").l
        assignmentToA.astChildren.isIdentifier.size shouldBe 1

        val List(fieldAccessA) = assignmentToA.astChildren.isCall.codeExact("_tmp_0.a").l
        fieldAccessA.name shouldBe Operators.fieldAccess
        fieldAccessA.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
        fieldAccessA.astChildren.isFieldIdentifier.canonicalNameExact("a").size shouldBe 1

        val List(assignmentToB) = destructionBlock.astChildren.isCall.codeExact("b = _tmp_0.b").l
        assignmentToB.astChildren.isIdentifier.size shouldBe 1

        val List(fieldAccessB) = assignmentToB.astChildren.isCall.codeExact("_tmp_0.b").l
        fieldAccessB.name shouldBe Operators.fieldAccess
        fieldAccessB.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
        fieldAccessB.astChildren.isFieldIdentifier.canonicalNameExact("b").size shouldBe 1

        val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
        tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for object destruction assignment with rest" in AstFixture("var {a, ...rest} = x") { cpg =>
      val List(program)      = cpg.method.nameExact(":program").l
      val List(programBlock) = program.astChildren.isBlock.l
      cpg.local.nameExact("a").size shouldBe 1

      val List(destructionBlock) = programBlock.astChildren.isBlock.l
      destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
      destructionBlock.astChildren.isCall.codeExact("_tmp_0 = x").size shouldBe 1

      val List(assignmentToA) = destructionBlock.astChildren.isCall.codeExact("a = _tmp_0.a").l
      assignmentToA.astChildren.isIdentifier.size shouldBe 1

      val List(fieldAccessA) = assignmentToA.astChildren.isCall.codeExact("_tmp_0.a").l
      fieldAccessA.name shouldBe Operators.fieldAccess
      fieldAccessA.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      fieldAccessA.astChildren.isFieldIdentifier.canonicalNameExact("a").size shouldBe 1

      val List(unknownRest) = destructionBlock.astChildren.codeExact("...rest").l
      unknownRest.label shouldBe "UNKNOWN"

      val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
      tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for object destruction assignment with computed property name" ignore AstFixture(
      "var {[propName]: n} = x"
    ) { _ => }

    "have correct structure for nested object destruction assignment with defaults as parameter" in AstFixture("""
        |function userId({id = {}, b} = {}) {
        |  return id;
        |}""".stripMargin) { cpg =>
      val List(userId) = cpg.method.nameExact("userId").l

      val List(param) = userId.parameter.nameExact("param1_0").l
      param.code shouldBe "{id, b}"

      val List(userIdBlock) = userId.astChildren.isBlock.l

      val List(destructionBlock) = userIdBlock.astChildren.isBlock.order(1).l
      destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
      destructionBlock.astChildren.isCall.codeExact("_tmp_0 = param1_0 === void 0 ? {} : param1_0").size shouldBe 1

      val List(assignmentToId) =
        destructionBlock.astChildren.isCall.codeExact("id = _tmp_0.id === void 0 ? {} : _tmp_0.id").l

      destructionBlock.astChildren.isCall.codeExact("b = _tmp_0.b").size shouldBe 1

      assignmentToId.astChildren.isIdentifier.size shouldBe 1

      val List(ternaryId) = assignmentToId.astChildren.isCall.codeExact("_tmp_0.id === void 0 ? {} : _tmp_0.id").l

      val List(indexAccessId) = ternaryId.astChildren.isCall.codeExact("_tmp_0.id").l
      indexAccessId.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      indexAccessId.astChildren.isFieldIdentifier.canonicalNameExact("id").size shouldBe 1

      val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
      tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for object destruction assignment as parameter" in AstFixture("""
        |function userId({id}) {
        |  return id;
        |}""".stripMargin) { cpg =>
      val List(userId)      = cpg.method.nameExact("userId").l
      val List(userIdBlock) = userId.astChildren.isBlock.l
      userIdBlock.astChildren.isLocal.nameExact("id").size shouldBe 1

      val List(assignmentToId) = userIdBlock.astChildren.isCall.codeExact("id = param1_0.id").l
      assignmentToId.astChildren.isIdentifier.size shouldBe 1

      val List(indexAccessId) = assignmentToId.astChildren.isCall.codeExact("param1_0.id").l
      indexAccessId.astChildren.isIdentifier.nameExact("param1_0").size shouldBe 1
      indexAccessId.astChildren.isFieldIdentifier.canonicalNameExact("id").size shouldBe 1
    }

    "have correct structure for array destruction assignment with declaration" in AstFixture("var [a, b] = x;") { cpg =>
      val List(program)      = cpg.method.nameExact(":program").l
      val List(programBlock) = program.astChildren.isBlock.l
      cpg.local.nameExact("a").size shouldBe 1
      cpg.local.nameExact("b").size shouldBe 1

      val List(destructionBlock) = programBlock.astChildren.isBlock.l
      destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
      destructionBlock.astChildren.isCall.codeExact("_tmp_0 = x").size shouldBe 1

      val List(assignmentToA) = destructionBlock.astChildren.isCall.codeExact("a = _tmp_0[0]").l
      assignmentToA.astChildren.isIdentifier.size shouldBe 1

      val List(indexAccessA) = assignmentToA.astChildren.isCall.codeExact("_tmp_0[0]").l
      indexAccessA.name shouldBe Operators.indexAccess

      indexAccessA.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      indexAccessA.astChildren.codeExact("0").size shouldBe 1

      val List(assignmentToB) = destructionBlock.astChildren.isCall.codeExact("b = _tmp_0[1]").l
      assignmentToB.astChildren.isIdentifier.size shouldBe 1

      val List(indexAccessB) = assignmentToB.astChildren.isCall.codeExact("_tmp_0[1]").l
      indexAccessB.name shouldBe Operators.indexAccess
      indexAccessB.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      indexAccessB.astChildren.isLiteral.codeExact("1").size shouldBe 1

      val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
      tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for array destruction assignment without declaration" in AstFixture("[a, b] = x;") { cpg =>
      val List(program)      = cpg.method.nameExact(":program").l
      val List(programBlock) = program.astChildren.isBlock.l
      cpg.local.nameExact("a").size shouldBe 1
      cpg.local.nameExact("b").size shouldBe 1

      val List(destructionBlock) = programBlock.astChildren.isBlock.l
      destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
      destructionBlock.astChildren.isCall.codeExact("_tmp_0 = x").size shouldBe 1

      val List(assignmentToA) = destructionBlock.astChildren.isCall.codeExact("a = _tmp_0[0]").l
      assignmentToA.astChildren.isIdentifier.size shouldBe 1

      val List(indexAccessA) = assignmentToA.astChildren.isCall.codeExact("_tmp_0[0]").l
      indexAccessA.name shouldBe Operators.indexAccess
      indexAccessA.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      indexAccessA.astChildren.isLiteral.codeExact("0").size shouldBe 1

      val List(assignmentToB) = destructionBlock.astChildren.isCall.codeExact("b = _tmp_0[1]").l
      assignmentToB.astChildren.isIdentifier.size shouldBe 1

      val List(indexAccessB) = assignmentToB.astChildren.isCall.codeExact("_tmp_0[1]").l
      indexAccessB.name shouldBe Operators.indexAccess
      indexAccessB.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      indexAccessB.astChildren.isLiteral.codeExact("1").size shouldBe 1

      val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
      tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for array destruction assignment with defaults" in AstFixture("var [a = 1, b = 2] = x;") {
      cpg =>
        val List(program)      = cpg.method.nameExact(":program").l
        val List(programBlock) = program.astChildren.isBlock.l
        programBlock.astChildren.isLocal.nameExact("a").size shouldBe 1
        programBlock.astChildren.isLocal.nameExact("b").size shouldBe 1

        val List(destructionBlock) = programBlock.astChildren.isBlock.l
        destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
        destructionBlock.astChildren.isCall.codeExact("_tmp_0 = x").size shouldBe 1

        val List(assignmentToA) = destructionBlock.assignment.codeExact("a = _tmp_0[0] === void 0 ? 1 : _tmp_0[0]").l
        assignmentToA.astChildren.isIdentifier.size shouldBe 1

        val List(ifA) = assignmentToA.astChildren.isCall.codeExact("_tmp_0[0] === void 0 ? 1 : _tmp_0[0]").l
        ifA.name shouldBe Operators.conditional

        val List(testA) = ifA.astChildren.isCall.codeExact("_tmp_0[0] === void 0").l
        testA.name shouldBe Operators.equals

        val List(testAIndexAccess) = testA.astChildren.isCall.codeExact("_tmp_0[0]").l
        testAIndexAccess.name shouldBe Operators.indexAccess

        testA.astChildren.isCall.codeExact("void 0").size shouldBe 1

        ifA.astChildren.isLiteral.codeExact("1").size shouldBe 1

        val List(falseBranchA) = ifA.astChildren.isCall.codeExact("_tmp_0[0]").l
        falseBranchA.name shouldBe Operators.indexAccess

        val List(assignmentToB) = destructionBlock.assignment.codeExact("b = _tmp_0[1] === void 0 ? 2 : _tmp_0[1]").l
        assignmentToB.astChildren.isIdentifier.size shouldBe 1

        val List(ifB) = assignmentToB.astChildren.isCall.codeExact("_tmp_0[1] === void 0 ? 2 : _tmp_0[1]").l
        ifB.name shouldBe Operators.conditional

        val List(testB) = ifB.astChildren.isCall.codeExact("_tmp_0[1] === void 0").l
        testB.name shouldBe Operators.equals

        val List(testBIndexAccess) = testB.astChildren.isCall.codeExact("_tmp_0[1]").l
        testBIndexAccess.name shouldBe Operators.indexAccess

        testB.astChildren.isCall.codeExact("void 0").size shouldBe 1

        ifB.astChildren.isLiteral.codeExact("2").size shouldBe 1

        val List(falseBranchB) = ifB.astChildren.isCall.codeExact("_tmp_0[1]").l

        falseBranchB.name shouldBe Operators.indexAccess

        val List(returnIdentifier) = destructionBlock.astChildren.isIdentifier.l
        returnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for array destruction assignment with ignores" in AstFixture("var [a, , b] = x;") { cpg =>
      val List(program)      = cpg.method.nameExact(":program").l
      val List(programBlock) = program.astChildren.isBlock.l
      cpg.local.nameExact("a").size shouldBe 1
      cpg.local.nameExact("b").size shouldBe 1

      val List(destructionBlock) = programBlock.astChildren.isBlock.l
      destructionBlock.astChildren.isLocal.nameExact("_tmp_0").size shouldBe 1
      destructionBlock.astChildren.isCall.codeExact("_tmp_0 = x").size shouldBe 1

      val List(assignmentToA) = destructionBlock.astChildren.isCall.codeExact("a = _tmp_0[0]").l
      assignmentToA.astChildren.isIdentifier.size shouldBe 1

      val List(indexAccessA) = assignmentToA.astChildren.isCall.codeExact("_tmp_0[0]").l
      indexAccessA.name shouldBe Operators.indexAccess
      indexAccessA.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      indexAccessA.astChildren.isLiteral.codeExact("0").size shouldBe 1

      val List(assignmentToB) = destructionBlock.astChildren.isCall.codeExact("b = _tmp_0[2]").l
      assignmentToB.astChildren.isIdentifier.size shouldBe 1

      val List(indexAccessB) = assignmentToB.astChildren.isCall.codeExact("_tmp_0[2]").l
      indexAccessB.name shouldBe Operators.indexAccess
      indexAccessB.astChildren.isIdentifier.nameExact("_tmp_0").size shouldBe 1
      indexAccessB.astChildren.isLiteral.codeExact("2").size shouldBe 1

      val List(tmpReturnIdentifier) = destructionBlock.astChildren.isIdentifier.l
      tmpReturnIdentifier.name shouldBe "_tmp_0"
    }

    "have correct structure for array destruction assignment with rest" ignore AstFixture("var [a, ...rest] = x") { _ =>
    }

    "have correct structure for array destruction as parameter" in AstFixture("""
        |function userId([id]) {
        |  return id;
        |}""".stripMargin) { cpg =>
      val List(userId) = cpg.method.nameExact("userId").l
      userId.parameter.nameExact("param1_0").size shouldBe 1
      val List(userIdBlock) = userId.astChildren.isBlock.l
      userIdBlock.astChildren.isLocal.nameExact("id").size shouldBe 1
      userIdBlock.astChildren.isCall.codeExact("id = param1_0.id").size shouldBe 1
    }

    "have correct structure for method spread argument" ignore AstFixture("foo(...args)") { _ => }
  }

  "AST generation for await/async" should {
    "have correct structure for await/async" in AstFixture("async function x(foo) { await foo() }") { cpg =>
      val List(awaitCall) = cpg.method.nameExact("x").astChildren.isBlock.astChildren.isCall.l
      awaitCall.code shouldBe "await foo()"
      awaitCall.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH
      awaitCall.methodFullName shouldBe "<operator>.await"
      awaitCall.astChildren.isCall.codeExact("foo()").size shouldBe 1
    }
  }

  "AST generation for instanceof/delete" should {
    "have correct structure for instanceof" in AstFixture("x instanceof Foo;") { cpg =>
      val List(program) = cpg.method.nameExact(":program").l

      val List(instanceOf) = program.astChildren.isBlock.astChildren.isCall.codeExact("x instanceof Foo").l
      instanceOf.name shouldBe Operators.instanceOf

      val List(lhs) = instanceOf.astChildren.isIdentifier.nameExact("x").l
      lhs.code shouldBe "x"
      val List(lhsArg) = instanceOf.argument.isIdentifier.nameExact("x").l
      lhsArg.code shouldBe "x"

      val List(rhs) = instanceOf.astChildren.isIdentifier.nameExact("Foo").l
      rhs.code shouldBe "Foo"
      val List(rhsArg) = instanceOf.argument.isIdentifier.nameExact("Foo").l
      rhsArg.code shouldBe "Foo"
    }

    "have correct structure for delete" in AstFixture("delete foo.x;") { cpg =>
      val List(program) = cpg.method.nameExact(":program").l

      val List(delete) = program.astChildren.isBlock.astChildren.isCall.codeExact("delete foo.x").l
      delete.name shouldBe Operators.delete

      val List(rhs) = delete.fieldAccess.l
      rhs.code shouldBe "foo.x"
    }
  }

  "AST generation for default parameters" should {
    "have correct structure for method parameter with default" in AstFixture("function foo(a = 1) {}") { cpg =>
      val List(foo) = cpg.method.nameExact("foo").l

      val List(paramA) = foo.parameter.nameExact("a").l
      paramA.order shouldBe 1
      paramA.index shouldBe 1

      val List(block)      = foo.astChildren.isBlock.l
      val List(assignment) = block.astChildren.isCall.l
      assignment.astChildren.isIdentifier.nameExact("a").size shouldBe 1

      val List(ternaryCall) = assignment.astChildren.isCall.nameExact(Operators.conditional).l
      val List(testCall)    = ternaryCall.astChildren.isCall.nameExact(Operators.equals).l
      testCall.astChildren.isIdentifier.nameExact("a").size shouldBe 1
      testCall.astChildren.isCall.nameExact("<operator>.void").size shouldBe 1
      ternaryCall.astChildren.isLiteral.codeExact("1").size shouldBe 1
      ternaryCall.astChildren.isIdentifier.nameExact("a").size shouldBe 1
    }

    "have correct structure for multiple method parameters with default" in AstFixture(
      "function foo(a = 1, b = 2) {}"
    ) { cpg =>
      val List(foo)    = cpg.method.nameExact("foo").l
      val List(paramA) = foo.parameter.nameExact("a").l
      paramA.order shouldBe 1
      paramA.index shouldBe 1

      val List(paramB) = foo.parameter.nameExact("b").l
      paramB.order shouldBe 2
      paramB.index shouldBe 2

      val List(block) = foo.astChildren.isBlock.l

      val List(assignmentA) = block.astChildren.isCall.codeExact("a = a === void 0 ? 1 : a").l
      assignmentA.astChildren.isIdentifier.nameExact("a").size shouldBe 1

      val List(ternaryCallA) = assignmentA.astChildren.isCall.nameExact(Operators.conditional).l
      val List(testCallA) =
        ternaryCallA.astChildren.isCall.nameExact(Operators.equals).l
      testCallA.astChildren.isIdentifier.nameExact("a").size shouldBe 1
      testCallA.astChildren.isCall.nameExact("<operator>.void").size shouldBe 1
      ternaryCallA.astChildren.isLiteral.codeExact("1").size shouldBe 1
      ternaryCallA.astChildren.isIdentifier.nameExact("a").size shouldBe 1

      val List(assignmentB) = block.astChildren.isCall.codeExact("b = b === void 0 ? 2 : b").l
      assignmentB.astChildren.isIdentifier.nameExact("b").size shouldBe 1

      val List(ternaryCallB) = assignmentB.astChildren.isCall.nameExact(Operators.conditional).l
      val List(testCallB)    = ternaryCallB.astChildren.isCall.nameExact(Operators.equals).l
      testCallB.astChildren.isIdentifier.nameExact("b").size shouldBe 1
      testCallB.astChildren.isCall.nameExact("<operator>.void").size shouldBe 1
      ternaryCallB.astChildren.isLiteral.codeExact("2").size shouldBe 1
      ternaryCallB.astChildren.isIdentifier.nameExact("b").size shouldBe 1
    }

    "have correct structure for method mixed parameters with default" in AstFixture("function foo(a, b = 1) {}") {
      cpg =>
        val List(foo)    = cpg.method.nameExact("foo").l
        val List(paramA) = foo.parameter.nameExact("a").l
        paramA.order shouldBe 1
        paramA.index shouldBe 1
        val List(paramB) = foo.parameter.nameExact("b").l
        paramB.order shouldBe 2
        paramB.index shouldBe 2

        val List(block)       = foo.astChildren.isBlock.l
        val List(assignmentB) = block.astChildren.isCall.codeExact("b = b === void 0 ? 1 : b").l
        assignmentB.astChildren.isIdentifier.nameExact("b").size shouldBe 1

        val List(ternaryCallB) = assignmentB.astChildren.isCall.nameExact(Operators.conditional).l
        val List(testCallB)    = ternaryCallB.astChildren.isCall.nameExact(Operators.equals).l
        testCallB.astChildren.isIdentifier.nameExact("b").size shouldBe 1
        testCallB.astChildren.isCall.nameExact("<operator>.void").size shouldBe 1
        ternaryCallB.astChildren.isLiteral.codeExact("1").size shouldBe 1
        ternaryCallB.astChildren.isIdentifier.nameExact("b").size shouldBe 1
    }

    "have correct structure for multiple method mixed parameters with default" in AstFixture(
      "function foo(a, b = 1, c = 2) {}"
    ) { cpg =>
      val List(foo)    = cpg.method.nameExact("foo").l
      val List(paramA) = foo.parameter.nameExact("a").l
      paramA.order shouldBe 1
      paramA.index shouldBe 1
      val List(paramB) = foo.parameter.nameExact("b").l
      paramB.order shouldBe 2
      paramB.index shouldBe 2
      val List(paramC) = foo.parameter.nameExact("c").l
      paramC.order shouldBe 3
      paramC.index shouldBe 3

      val List(block)       = foo.astChildren.isBlock.l
      val List(assignmentB) = block.astChildren.isCall.codeExact("b = b === void 0 ? 1 : b").l
      assignmentB.astChildren.isIdentifier.nameExact("b").size shouldBe 1

      val List(ternaryCallB) = assignmentB.astChildren.isCall.nameExact(Operators.conditional).l
      val List(testCallB)    = ternaryCallB.astChildren.isCall.nameExact(Operators.equals).l
      testCallB.astChildren.isIdentifier.nameExact("b").size shouldBe 1
      testCallB.astChildren.isCall.nameExact("<operator>.void").size shouldBe 1
      ternaryCallB.astChildren.isLiteral.codeExact("1").size shouldBe 1
      ternaryCallB.astChildren.isIdentifier.nameExact("b").size shouldBe 1

      val List(assignmentC) = block.astChildren.isCall.codeExact("c = c === void 0 ? 2 : c").l
      assignmentC.astChildren.isIdentifier.nameExact("c").size shouldBe 1

      val List(ternaryCallC) = assignmentC.astChildren.isCall.nameExact(Operators.conditional).l
      val List(testCallC)    = ternaryCallC.astChildren.isCall.nameExact(Operators.equals).l
      testCallC.astChildren.isIdentifier.nameExact("c").size shouldBe 1
      testCallC.astChildren.isCall.nameExact("<operator>.void").size shouldBe 1
      ternaryCallC.astChildren.isLiteral.codeExact("2").size shouldBe 1
      ternaryCallC.astChildren.isIdentifier.nameExact("c").size shouldBe 1
    }
  }

  "AST generation for global builtins" should {
    "have correct structure for JSON.parse" in AstFixture("""JSON.parse("foo");""") { cpg =>
      val List(program)     = cpg.method.nameExact(":program").l
      val List(methodBlock) = program.astChildren.isBlock.l
      val List(parseCall)   = methodBlock.astChildren.isCall.l
      parseCall.name shouldBe "parse"
      parseCall.methodFullName shouldBe "JSON.parse"
      parseCall.code shouldBe """JSON.parse("foo")"""
      parseCall.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH

      val List(argument) = parseCall.astChildren.isLiteral.codeExact(""""foo"""").l
      argument.order shouldBe 1
      argument.argumentIndex shouldBe 1
    }

    "have correct structure for JSON.stringify" in AstFixture("""JSON.stringify(foo);""") { cpg =>
      val List(program)     = cpg.method.nameExact(":program").l
      val List(methodBlock) = program.astChildren.isBlock.l
      val List(parseCall)   = methodBlock.astChildren.isCall.l
      parseCall.name shouldBe "stringify"
      parseCall.methodFullName shouldBe "JSON.stringify"
      parseCall.code shouldBe """JSON.stringify(foo)"""
      parseCall.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH

      val List(argument) = parseCall.astChildren.isIdentifier.nameExact("foo").l
      argument.code shouldBe "foo"
      argument.order shouldBe 1
      argument.argumentIndex shouldBe 1
    }

    "not create static builtin call for calls not exactly matching dictionary" in AstFixture(
      """JSON.parse.apply("foo");"""
    ) { cpg =>
      val List(program)     = cpg.method.nameExact(":program").l
      val List(methodBlock) = program.astChildren.isBlock.l
      val List(parseCall)   = methodBlock.astChildren.isCall.l
      parseCall.dispatchType shouldBe DispatchTypes.DYNAMIC_DISPATCH
    }
  }

  "AST generation for dependencies" should {
    "have no dependencies if none are declared at all" in AstFixture("var x = 1;") { cpg =>
      getDependencies(cpg).size shouldBe 0
    }

    "have correct dependencies (imports)" in AstFixture("""
        |import {a} from "depA";
        |import {b} from "depB";
        |""".stripMargin) { cpg =>
      val deps = getDependencies(cpg).l
      deps.size shouldBe 2

      val List(depA) = deps.name("a").l
      depA.dependencyGroupId shouldBe Some("depA")

      val List(depB) = deps.name("b").l
      depB.dependencyGroupId shouldBe Some("depB")
    }

    "have correct import nodes" in AstFixture("""
        |import {a} from "depA";
        |import {b} from "depB";
        |""".stripMargin) { cpg =>
      val List(x: Import, y: Import) = cpg.imports.l
      x.code shouldBe "import {a} from \"depA\""
      x.importedEntity shouldBe Some("depA")
      y.code shouldBe "import {b} from \"depB\""
      y.importedEntity shouldBe Some("depB")
      x.astIn.l match {
        case List(n: NamespaceBlock) =>
          n.fullName shouldBe "code.js:<global>"
        case _ => fail()
      }
    }

    "have correct dependencies (require)" in AstFixture("""
        |const a = require("depA");
        |const b = require("depB");
        |""".stripMargin) { cpg =>
      val deps = getDependencies(cpg).l
      deps.size shouldBe 2

      val List(depA) = deps.name("a").l
      depA.dependencyGroupId shouldBe Some("depA")
      depA.version shouldBe "require"

      val List(depB) = deps.name("b").l
      depB.dependencyGroupId shouldBe Some("depB")
      depB.version shouldBe "require"
    }

    "have correct dependencies (strange requires)" in AstFixture("""
        |var _ = require("depA");
        |var b = require("depB").some.strange().call().here;
        |var { c } = require('depC');
        |var { d, e } = require('depD');
        |var [ f, g ] = require('depE');
        |""".stripMargin) { cpg =>
      val deps = getDependencies(cpg).l
      deps.size shouldBe 7

      val List(depA) = deps.name("_").l
      depA.dependencyGroupId shouldBe Some("depA")
      depA.version shouldBe "require"

      val List(depB) = deps.name("b").l
      depB.dependencyGroupId shouldBe Some("depB")
      depB.version shouldBe "require"

      val List(depC) = deps.name("c").l
      depC.dependencyGroupId shouldBe Some("depC")
      depC.version shouldBe "require"

      val List(depD) = deps.name("d").l
      depD.dependencyGroupId shouldBe Some("depD")
      depD.version shouldBe "require"

      val List(depE) = deps.name("e").l
      depE.dependencyGroupId shouldBe Some("depD")
      depE.version shouldBe "require"

      val List(depF) = deps.name("f").l
      depF.dependencyGroupId shouldBe Some("depE")
      depF.version shouldBe "require"

      val List(depG) = deps.name("g").l
      depG.dependencyGroupId shouldBe Some("depE")
      depG.version shouldBe "require"
    }

    "have correct dependencies (mixed)" in AstFixture("""
        |import {a} from "depA";
        |const b = require("depB");
        |""".stripMargin) { cpg =>
      val deps = getDependencies(cpg).l
      deps.size shouldBe 2

      val List(depA) = deps.name("a").l
      depA.dependencyGroupId shouldBe Some("depA")

      val List(depB) = deps.name("b").l
      depB.dependencyGroupId shouldBe Some("depB")
    }

    "have correct dependencies (different variations of import)" in AstFixture("""
       |import name from "module-name";
       |import * as otherName from "module-name";
       |import { member1 } from "module-name";
       |import { member2 as alias1 } from "module-name";
       |import { member3 , member4 } from "module-name";
       |import { member5 , member6 as alias2 } from "module-name";
       |import defaultMember1, * as alias3 from "module-name";
       |import defaultMember2 from "module-name";
       |import "module-name";
       |""".stripMargin) { cpg =>
      val deps = getDependencies(cpg).l
      deps.size shouldBe 12

      deps.name("name").dependencyGroupId("module-name").size shouldBe 1
      deps.name("otherName").dependencyGroupId("module-name").size shouldBe 1
      deps.name("member1").dependencyGroupId("module-name").size shouldBe 1
      deps.name("alias1").dependencyGroupId("module-name").size shouldBe 1
      deps.name("member3").dependencyGroupId("module-name").size shouldBe 1
      deps.name("member4").dependencyGroupId("module-name").size shouldBe 1
      deps.name("member5").dependencyGroupId("module-name").size shouldBe 1
      deps.name("alias2").dependencyGroupId("module-name").size shouldBe 1
      deps.name("defaultMember1").dependencyGroupId("module-name").size shouldBe 1
      deps.name("alias3").dependencyGroupId("module-name").size shouldBe 1
      deps.name("defaultMember2").dependencyGroupId("module-name").size shouldBe 1
      deps.name("module-name").dependencyGroupId("module-name").size shouldBe 1
    }
  }

}

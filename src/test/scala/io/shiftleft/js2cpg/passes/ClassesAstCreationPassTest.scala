package io.shiftleft.js2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.*
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.JS_SUFFIX
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.semanticcpg.language.*

class ClassesAstCreationPassTest extends AbstractPassTest {

  "AST generation for classes" should {
    "have a TYPE_DECL and <meta> TYPE_DECL for ClassA" in AstFixture("var x = class ClassA {}") { cpg =>
      val List(classAMetaTypeDecl) =
        cpg.typeDecl.nameExact("ClassA<meta>").fullNameExact("code.js::program:ClassA<meta>").l
      val List(classATypeDecl) = cpg.typeDecl.nameExact("ClassA").fullNameExact("code.js::program:ClassA").l
    }

    "have constructor binding in <meta> TYPE_DECL for ClassA" in AstFixture("""
        |var x = class ClassA {
        |  constructor() {}
        |}""".stripMargin) { cpg =>
      val List(classAMetaTypeDecl) =
        cpg.typeDecl.nameExact("ClassA<meta>").fullNameExact("code.js::program:ClassA<meta>").l
      val List(constructorBinding) = classAMetaTypeDecl.bindsOut.l
      constructorBinding.name shouldBe ""
      constructorBinding.signature shouldBe ""
      val List(boundMethod) = constructorBinding.refOut.l
      boundMethod.fullName shouldBe "code.js::program:ClassA<constructor>"
    }

    "have member for static method in <meta> TYPE_DECL for ClassA" in AstFixture("""
       |var x = class ClassA {
       |  static staticFoo() {}
       |}""".stripMargin) { cpg =>
      val List(classAMetaTypeDecl) =
        cpg.typeDecl.nameExact("ClassA<meta>").fullNameExact("code.js::program:ClassA<meta>").l
      val List(memberFoo) = classAMetaTypeDecl.member.l
      pendingUntilFixed {
        memberFoo.dynamicTypeHintFullName shouldBe Seq("code.js::program:ClassA:staticFoo")
      }
    }

    "have method for static method in ClassA AST" in AstFixture("""
        |var x = class ClassA {
        |  static staticFoo() {}
        |}""".stripMargin) { cpg =>
      val List(classATypeDecl) =
        cpg.typeDecl.nameExact("ClassA").fullNameExact("code.js::program:ClassA").l
      val List(methodFoo) = classATypeDecl.method.nameExact("staticFoo").l
      pendingUntilFixed {
        methodFoo.fullName shouldBe "code.js::program:ClassA:staticFoo"
      }
    }

    "have member for non-static method in TYPE_DECL for ClassA" in AstFixture("""
        |var x = class ClassA {
        |  foo() {}
        |}""".stripMargin) { cpg =>
      val List(classATypeDecl) =
        cpg.typeDecl.nameExact("ClassA").fullNameExact("code.js::program:ClassA").l
      val List(memberFoo) = classATypeDecl.member.l
      pendingUntilFixed {
        memberFoo.dynamicTypeHintFullName shouldBe Seq("code.js::program:ClassA:foo")
      }
    }

    "have method for non-static method in ClassA AST" in AstFixture("""
        |var x = class ClassA {
        |  foo() {}
        |}""".stripMargin) { cpg =>
      val List(classATypeDecl) =
        cpg.typeDecl.nameExact("ClassA").fullNameExact("code.js::program:ClassA").l
      val List(methodFoo) = classATypeDecl.method.nameExact("foo").l
      pendingUntilFixed {
        methodFoo.fullName shouldBe "code.js::program:ClassA:foo"
      }
    }

    "have TYPE_REF to <meta> for ClassA" in AstFixture("var x = class ClassA {}") { cpg =>
      val List(program)         = cpg.method.nameExact(":program").l
      val List(programBlock)    = program.astChildren.isBlock.l
      val List(assignmentToTmp) = programBlock.astChildren.isCall.l
      val List(rhs)             = assignmentToTmp.astChildren.isTypeRef.l
      rhs.typeFullName shouldBe "code.js::program:ClassA<meta>"
    }

    "have correct structure for type decls for classes with extends" in AstFixture("class ClassA extends Base {}") {
      cpg =>
        val List(classATypeDecl) = cpg.typeDecl.nameExact("ClassA").l
        pendingUntilFixed {
          // Currently we do not handle class inheritance.
          classATypeDecl.inheritsFromTypeFullName shouldBe Seq("Base")
        }
    }
  }

  "AST generation for constructor" should {
    "have correct structure for simple new" in AstFixture("new MyClass()") { cpg =>
      val List(program) = cpg.method.nameExact(":program").l
      val List(newCall) =
        program.astChildren.isBlock.codeExact("new MyClass()").l
      val List(block)    = newCall.astChildren.isBlock.l
      val tmpName        = "_tmp_0"
      val List(localTmp) = block.astChildren.isLocal.l
      localTmp.name shouldBe tmpName

      val List(tmpAssignment) = block.astChildren.isCall.codeExact(tmpName + " = .alloc").l
      tmpAssignment.name shouldBe Operators.assignment

      val List(tmp) = tmpAssignment.astChildren.isIdentifier.l
      tmp.code shouldBe tmpName
      tmp.name shouldBe tmpName

      val List(allocCall) = tmpAssignment.astChildren.isCall.l
      allocCall.name shouldBe ".alloc"
      allocCall.code shouldBe ".alloc"

      val List(constructorCall) = block.astChildren.isCall.codeExact("MyClass()").l
      val List(name)            = constructorCall.astChildren.isIdentifier.nameExact("MyClass").l
      val List(receiver)        = constructorCall.receiver.isIdentifier.nameExact("MyClass").l
      val List(tmpArg0)         = constructorCall.astChildren.isIdentifier.nameExact(tmpName).l
      tmpArg0.argumentIndex shouldBe 0

      val List(tmpArg0Argument) = constructorCall.argument.isIdentifier.nameExact(tmpName).l
      tmpArg0Argument.argumentIndex shouldBe 0

      val List(returnTmp) = block.astChildren.isIdentifier.l
      returnTmp.name shouldBe tmpName
    }

    "have correct structure for simple new with arguments" in AstFixture("new MyClass(arg1, arg2)") { cpg =>
      val List(program) = cpg.method.nameExact(":program").l
      val List(newCall) =
        program.astChildren.isBlock.codeExact("new MyClass(arg1, arg2)").l
      val List(block)    = newCall.astChildren.isBlock.l
      val tmpName        = "_tmp_0"
      val List(localTmp) = block.astChildren.isLocal.l
      localTmp.name shouldBe tmpName
      val List(tmpAssignment) = block.astChildren.isCall.codeExact(tmpName + " = .alloc").l
      tmpAssignment.name shouldBe Operators.assignment
      val List(tmp) = tmpAssignment.astChildren.isIdentifier.l
      tmp.code shouldBe tmpName
      tmp.name shouldBe tmpName

      val List(allocCall) = tmpAssignment.astChildren.isCall.l
      allocCall.name shouldBe ".alloc"
      allocCall.code shouldBe ".alloc"

      val List(constructorCall) = block.astChildren.isCall.codeExact("MyClass(arg1, arg2)").l
      val List(name)            = constructorCall.astChildren.isIdentifier.nameExact("MyClass").l
      val List(receiver)        = constructorCall.receiver.isIdentifier.nameExact("MyClass").l
      val List(tmpArg0)         = constructorCall.astChildren.isIdentifier.nameExact(tmpName).l
      tmpArg0.argumentIndex shouldBe 0

      val List(tmpArg0Argument) = constructorCall.argument.isIdentifier.nameExact(tmpName).l
      tmpArg0Argument.argumentIndex shouldBe 0

      val List(arg1) = constructorCall.argument.isIdentifier.nameExact("arg1").l
      arg1.argumentIndex shouldBe 1

      val List(arg2) = constructorCall.argument.isIdentifier.nameExact("arg2").l
      arg2.argumentIndex shouldBe 2

      val List(returnTmp) = block.astChildren.isIdentifier.l
      returnTmp.name shouldBe tmpName
    }

    "have correct structure for new with access path" in AstFixture("new foo.bar.MyClass()") { cpg =>
      val List(program) = cpg.method.nameExact(":program").l
      val List(newCall) =
        program.astChildren.isBlock.codeExact("new foo.bar.MyClass()").l
      val List(block)    = newCall.astChildren.isBlock.l
      val tmpName        = "_tmp_0"
      val List(localTmp) = block.astChildren.isLocal.l
      localTmp.name shouldBe tmpName
      val List(tmpAssignment) = block.astChildren.isCall.codeExact(tmpName + " = .alloc").l
      tmpAssignment.name shouldBe Operators.assignment
      val List(tmp) = tmpAssignment.astChildren.isIdentifier.l
      tmp.code shouldBe tmpName
      tmp.name shouldBe tmpName

      val List(allocCall) = tmpAssignment.astChildren.isCall.l
      allocCall.name shouldBe ".alloc"
      allocCall.code shouldBe ".alloc"

      val List(constructorCall) = block.astChildren.isCall.codeExact("foo.bar.MyClass()").l
      val List(path)            = constructorCall.astChildren.isCall.codeExact("foo.bar.MyClass").l
      path.name shouldBe Operators.fieldAccess

      val List(receiver) = constructorCall.receiver.isCall.codeExact("foo.bar.MyClass").l
      receiver.name shouldBe Operators.fieldAccess

      val List(tmpArg0) = constructorCall.astChildren.isIdentifier.nameExact(tmpName).l
      tmpArg0.argumentIndex shouldBe 0

      val List(tmpArg0Argument) = constructorCall.argument.isIdentifier.nameExact(tmpName).l
      tmpArg0Argument.argumentIndex shouldBe 0

      val List(returnTmp) = block.astChildren.isIdentifier.l
      returnTmp.name shouldBe tmpName
    }

    "have correct structure for throw new exceptions" in AstFixture("function() { throw new Foo() }") { cpg =>
      val List(program) = cpg.method.nameExact(":program").l

      val List(throwCall) =
        cpg.method
          .nameExact("anonymous")
          .astChildren
          .isBlock
          .astChildren
          .codeExact("throw new Foo()")
          .l

      val List(block) = throwCall.astChildren.isBlock.l

      val tmpName = "_tmp_0"

      val List(localTmp) = block.astChildren.isLocal.l
      localTmp.name shouldBe tmpName

      val List(tmpAssignment) = block.astChildren.isCall.codeExact(tmpName + " = .alloc").l
      tmpAssignment.name shouldBe Operators.assignment
      val List(tmp) = tmpAssignment.astChildren.isIdentifier.l
      tmp.code shouldBe tmpName
      tmp.name shouldBe tmpName

      val List(allocCall) = tmpAssignment.astChildren.isCall.l
      allocCall.name shouldBe ".alloc"
      allocCall.code shouldBe ".alloc"

      val List(constructorCall) = block.astChildren.isCall.codeExact("Foo()").l
      val List(name)            = constructorCall.astChildren.isIdentifier.nameExact("Foo").l
      val List(receiver)        = constructorCall.receiver.isIdentifier.nameExact("Foo").l
      val List(tmpArg0)         = constructorCall.astChildren.isIdentifier.nameExact(tmpName).l
      tmpArg0.argumentIndex shouldBe 0

      val List(tmpArg0Argument) = constructorCall.argument.isIdentifier.nameExact(tmpName).l
      tmpArg0.argumentIndex shouldBe 0

      val List(returnTmp) = block.astChildren.isIdentifier.l
      returnTmp.name shouldBe tmpName
    }
  }

}

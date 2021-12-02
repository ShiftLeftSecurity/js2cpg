package io.shiftleft.js2cpg.dataflow

import io.joern.dataflowengineoss.language._
import io.shiftleft.codepropertygraph.generated.EdgeTypes
import io.shiftleft.semanticcpg.language._

class JSDataFlowTest1 extends DataFlowCodeToCpgSuite {

  override val code: String =
    """
      | function flows1(fd, mode) {
      |     var buff = [];
      |
      |     var sz = 0;
      |     if (mode == 1) sz = 20;
      |     if (mode == 2) sz = 200;
      |     if (mode == 3) sz = 41;
      |     if (mode == 5) sz = -5;
      |
      |     read(fd, buff, sz);
      | };
      """.stripMargin

  "Test 1: flow from function call read to multiple versions of the same variable" in {

    def source = cpg.identifier.name("sz").l
    def sink   = cpg.call.code("read.*").l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(
        List(("read(fd, buff, sz)", Some(11))),
        List(("sz = 0", Some(5)), ("read(fd, buff, sz)", Some(11))),
        List(("sz = 20", Some(6)), ("read(fd, buff, sz)", Some(11))),
        List(("sz = 200", Some(7)), ("read(fd, buff, sz)", Some(11))),
        List(("sz = 41", Some(8)), ("read(fd, buff, sz)", Some(11))),
        List(("sz = -5", Some(9)), ("read(fd, buff, sz)", Some(11)))
      )

    // pretty printing for flows
    def flowsPretty = flows.p.mkString
    flowsPretty.should(include("sz = 20"))
    flowsPretty.should(include("read(fd, buff, sz)"))
    val tmpSourceFile = flows.head.elements.head.method.filename
    flowsPretty.should(include(tmpSourceFile))
  }
}

class JSDataFlowTest3 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | function foo(x) {};
      |
      | function method(y){
      |  var a = 10;
      |  if (a < y){
      |    foo(a);
      |  };
      | };
      """.stripMargin

  "Test 3: flow from function call argument" in {
    implicit val callResolver: NoResolve.type = NoResolve
    def source                                = cpg.identifier.name("a").l
    def sink                                  = cpg.call.code("foo.*").argument.l
    def flows                                 = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(List(("foo(a)", Some(7))),
          List(("a = 10", Some(5)), ("a < y", Some(6)), ("foo(a)", Some(7))),
          List(("a < y", Some(6)), ("foo(a)", Some(7))))
  }
}

class JSDataFlowTest4 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | function flow() {
      |   var a = 0x37;
      |   var b=a;
      |   var c=0x31;
      |   var z = b + c;
      |   z++;
      |   var p = z;
      |   var x = z;
      | };
      """.stripMargin

  "Test 4: flow chains from x to a" in {
    def source = cpg.identifier.name("a").l
    def sink   = cpg.identifier.name("x").l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(
        List(("a = 55", Some(3)),
             ("b = a", Some(4)),
             ("b + c", Some(6)),
             ("z = b + c", Some(6)),
             ("z++", Some(7)),
             ("x = z", Some(9))),
        List(("b = a", Some(4)),
             ("b + c", Some(6)),
             ("z = b + c", Some(6)),
             ("z++", Some(7)),
             ("x = z", Some(9)))
      )
  }
}

class JSDataFlowTest5 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | function flow(a){
      |   var z = a;
      |   var b = z;
      |
      |   return b;
      | };
      """.stripMargin

  "Test 5: flow from method return to a" in {
    def source = cpg.identifier.name("a").l
    def sink   = cpg.method(".*flow").ast.isReturn.l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(
        List[(String, Option[Integer])](
          ("z = a", 3),
          ("b = z", 4),
          ("return b", 6),
        ))
  }
}

class JSDataFlowTest6 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | function nested(a){
      |   var x = 0;
      |   var z = 1;
      |   if(a < 10){
      |     if( a < 5){
      |       if(a < 2){
      |          x = a;
      |       }
      |     }
      |   } else
      |     x = z;
      |
      |   return x;
      | }
      """.stripMargin

  "Test 6: flow with nested if-statements from method return to a" in {
    def source = cpg.call.code("a < 10").argument.code("a").l
    def sink   = cpg.method(".*nested").ast.isReturn.l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(
        List[(String, Option[Integer])](
          ("a < 10", Some(5)),
          ("a < 5", Some(6)),
          ("a < 2", Some(7)),
          ("x = a", 8),
          ("return x", 14),
        ))
  }
}

class JSDataFlowTest7 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | function nested(a) {
      |   var x = 0;
      |   var z = 1;
      |   if(a < 10){
      |     if( a < 5){
      |       if(a < 2){
      |          x = a;
      |       }
      |     }
      |   } else
      |     x = z;
      |
      |   return x;
      | };
      """.stripMargin

  "Test 7: flow with nested if-statements to `return x`" in {
    def source = cpg.identifier.name("x").l
    def sink   = cpg.method(".*nested").ast.isReturn.l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(
        List(("return x", Some(14))),
        List(("x = z", Some(12)), ("return x", Some(14))),
        List(("x = 0", Some(3)), ("return x", Some(14))),
        List(("x = a", Some(8)), ("return x", Some(14)))
      )
  }
}

class JSDataFlowTest8 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | function foo(y) {};
      |
      | function param(x){
      |    var a = x;
      |    var b = a;
      |    var z = foo(b);
      |  }
      """.stripMargin

  "Test 8: flow chain from function argument of foo to a" in {
    implicit val callResolver: NoResolve.type = NoResolve
    def source                                = cpg.identifier.name("a").l
    def sink                                  = cpg.call.code("foo.*").argument.l
    def flows                                 = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe Set(
      List(("a = x", Some(5)), ("b = a", Some(6)), ("foo(b)", Some(7))),
      List(("b = a", Some(6)), ("foo(b)", Some(7))))

  }
}

class JSDataFlowTest9 extends DataFlowCodeToCpgSuite {
  override val code: String = """
      | function param(x){
      |    var a = x;
      |    var b = a;
      |    var z = foo(b);
      |  }
      """.stripMargin

  "Test 9: flow from function foo to a" in {
    def source = cpg.identifier.name("a").l
    def sink   = cpg.call.code("foo.*").argument(1).l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(List[(String, Option[Integer])](
            ("a = x", 3),
            ("b = a", 4),
            ("foo(b)", 5)
          ),
          List[(String, Option[Integer])](
            ("b = a", 4),
            ("foo(b)", 5)
          ))
  }
}

class JSDataFlowTest10 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | var node = {
      |  'value1' : 1,
      |  'value2' : 2
      | };
      |
      |function test(){
      |  var x = 10;
      |  node.value1 = x;
      |  node.value2 = node.value1;
      |}
      """.stripMargin

  "Test 10: flow with member access in expression to identifier x" in {
    def source = cpg.identifier.name("x").l
    def sink   = cpg.call.code("node.value2").l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(
        List(("node.value1 = x", Some(9)), ("node.value2 = node.value1", Some(10))),
        List(("x = 10", Some(8)),
             ("node.value1 = x", Some(9)),
             ("node.value2 = node.value1", Some(10)))
      )
  }
}

class JSDataFlowTest11 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | function flow() {
      |   var a = 37;
      |   var b = a;
      |   var c = 31;
      |   var z = b + c;
      |   z++;
      |   var p = z;
      |   var x = z;
      | }
      """.stripMargin

  "Test 11: flow chain from x to literal 37" in {
    def source = cpg.literal.code("37").l
    def sink   = cpg.identifier.name("x").l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(
        List(("a = 37", Some(3)),
             ("b = a", Some(4)),
             ("b + c", Some(6)),
             ("z = b + c", Some(6)),
             ("z++", Some(7)),
             ("x = z", Some(9))))
  }
}

class JSDataFlowTest12 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | function flow() {
      |    var a = 37;
      |    var b = a;
      |    var z = b;
      |    z+=a;
      | }
       """.stripMargin

  "Test 12: flow with short hand assignment operator" in {
    def source = cpg.call.code("a = 37").argument(2).l
    def sink   = cpg.call.code("z \\+= a").argument(1).l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(
        List[(String, Option[Integer])](
          ("a = 37", 3),
          ("b = a", 4),
          ("z = b", 5),
          ("z += a", 6)
        ))
  }
}

class JSDataFlowTest13 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | function flow() {
      |    var a = 37;
      |    var b = a;
      |    var z = b;
      |    z += a;
      |    var w = z;
      | }
      """.stripMargin

  "Test 13: flow after short hand assignment" in {
    def source = cpg.call.code("a = 37").argument(1).l
    def sink   = cpg.identifier.name("w").l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(
        List[(String, Option[Integer])](
          ("a = 37", 3),
          ("b = a", 4),
          ("z = b", 5),
          ("z += a", 6),
          ("w = z", 7)
        )
      )
  }
}

class JSDataFlowTest14 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | function main(argc, argv){
      |    var x = argv[1];
      |    var y = x;
      |    var z = y;
      |    return 0;
      | };
      """.stripMargin

  "Test 14: flow from array method parameter to identifier" in {
    def source = cpg.method(".*main").parameter
    def sink   = cpg.identifier.name("y")
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(
        List[(String, Option[Integer])](
          ("main(this, argc, argv)", 2),
          ("x = argv[1]", 3),
          ("y = x", 4),
          ("z = y", 5)
        ),
        List[(String, Option[Integer])](
          ("main(this, argc, argv)", 2),
          ("x = argv[1]", 3),
          ("y = x", 4)
        )
      )
  }
}

class JSDataFlowTest15 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
       |function foo(x, y) {
       |  var z =  x ? f(y) : g(y);
       |  return;
       | }
      """.stripMargin

  "Test 15: conditional expressions" in {
    def source = cpg.method.parameter.name("y")
    def sink   = cpg.identifier.name("z")
    def flows  = sink.reachableByFlows(source).l
    flows.size shouldBe 1
  }
}

class JSDataFlowTest16 extends DataFlowCodeToCpgSuite {
  override val code: String = """
    |function bar() {
    |  var x = source();
    |  foo(x);
    |};
    |
    |function foo(y) {
    |  sink(y);
    |};""".stripMargin

  "Test 16: find source in caller" in {
    def source = cpg.call.code("source.*").l
    def sink   = cpg.call.code("sink.*").argument(1).l
    def flows  = sink.reachableByFlows(source)

    pendingUntilFixed {
      flows.map(flowToResultPairs).toSet shouldBe Set(
        List(("source()", Some(4)),
             ("x = source()", Some(4)),
             ("foo(x)", Some(5)),
             ("foo(y)", Some(8)),
             ("sink(y)", Some(9))))
    }
  }
}

class JSDataFlowTest17 extends DataFlowCodeToCpgSuite {
  override val code: String = """
    |function bar() {
    |  return source();
    |};
    |
    |function foo(y) {
    |  var y = bar();
    |  sink(y);
    |};""".stripMargin

  "Test 17.1: find source in callee" in {
    def source = cpg.call.code("source.*").l
    def sink   = cpg.call.code("sink.*").argument(1).l
    def flows  = sink.reachableByFlows(source)

    pendingUntilFixed {
      flows.map(flowToResultPairs).toSet shouldBe Set(
        List(("source()", Some(4)),
             ("return source();", Some(4)),
             ("int", Some(3)),
             ("bar()", Some(8)),
             ("y = bar()", Some(8)),
             ("sink(y)", Some(9)))
      )
    }
  }

  "Test 17.2 : allow using formal parameters as sink" in {
    def source = cpg.call.code("source.*").l
    def sink   = cpg.method(".*sink").parameter.index(1).l
    def flows  = sink.reachableByFlows(source)

    pendingUntilFixed {
      flows.map(flowToResultPairs).toSet shouldBe Set(
        List(("source()", Some(4)),
             ("return source();", Some(4)),
             ("int", Some(3)),
             ("bar()", Some(8)),
             ("y = bar()", Some(8)),
             ("sink(y)", Some(9)),
             ("sink(p1)", None))
      )
    }
  }
}

class JSDataFlowTest18 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | var point = {
      |   'x' : 0,
      |   'y' : 0
      | };
      |
      | function source() {
      |   return 2.0;
      | };
      |
      | function sink(x) {
      |   return 3;
      | };
      |
      | function main() {
      |   var k = source();
      |   point.x = k;
      |   point.y = 2;
      |   sink(point.x);
      | };
      |""".stripMargin

  "Test 18: struct data flow" in {
    def source = cpg.call.code("source.*").l
    def sink   = cpg.call.code("sink.*").argument.l
    def flows  = sink.reachableByFlows(source).l

    flows.map(flowToResultPairs).toSet shouldBe Set(
      List(("source()", Some(16)),
           ("k = source()", Some(16)),
           ("point.x = k", Some(17)),
           ("sink(point.x)", Some(19))))

  }
}

class JSDataFlowTest22 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | var s { 'field' : 0 };
      |
      | function foo(arg) {
      |   arg.field = source();
      |   sink(arg.field);
      | }
      |""".stripMargin

  "Test 22: find flows (pointer-to-object)" in {
    def source = cpg.call.code("source.*").l
    def sink   = cpg.call.code("sink.*").argument.l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(
        List(("source()", Some(5)),
             ("arg.field = source()", Some(5)),
             ("sink(arg.field)", Some(6))))
  }

}

class JSDataFlowTest25 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      |function bar() {
      |  source(a.b);
      |  sink(a.b);
      |}
      |
      |""".stripMargin

  "Test 25: should report flow if access passed to source" in {
    def source = cpg.call.code("source.*").argument.l
    def sink   = cpg.call.code("sink.*").argument.l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe Set(
      List(("source(a.b)", Some(3)), ("sink(a.b)", Some(4))))
  }
}

class JSDataFlowTest27 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      |function foo(y, x) {
      |  free(y);
      |  free(x);
      |};
      |""".stripMargin

  "Test 27: find flows of last statements to METHOD_RETURN" in {
    def source = cpg.call.code("free.*").argument(1).l
    def sink   = cpg.method(".*foo").methodReturn.l
    def flows  = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(List(("free(y)", Some(3)), ("RET", Some(2))),
          List(("free(x)", Some(4)), ("RET", Some(2))))
  }
}

class JSDataFlowTest31 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
      | function foo() {
      |   return bar();
      | };
    """.stripMargin

  "Test 31: should not create edges from call to ret twice" in {
    cpg.call
      .code("bar.*")
      .outE(EdgeTypes.REACHING_DEF)
      .count(_.inNode() == cpg.ret.head) shouldBe 1
  }
}

class JSDataFlowTest32 extends DataFlowCodeToCpgSuite {
  override val code: String =
    """
       | function f(x, y) {
       |   g(x, y);
       | };""".stripMargin

  "Test 32: should find flow from outer params to inner params" in {
    def source = cpg.method(".*f").parameter.l
    def sink   = cpg.call.code("g.*").argument.l
    sink.size shouldBe 3   // incl. this
    source.size shouldBe 3 // incl. this

    def flows = sink.reachableByFlows(source)

    flows.map(flowToResultPairs).toSet shouldBe
      Set(List(("f(this, x, y)", Some(2)), ("g(x, y)", Some(3))))
  }
}

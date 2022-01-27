package io.shiftleft.js2cpg.dataflow

import io.joern.dataflowengineoss.language._
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.joern.dataflowengineoss.semanticsloader.{Parser, Semantics}
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes._
import io.shiftleft.semanticcpg.language._
import io.shiftleft.semanticcpg.layers._

class DataFlowCodeToCpgSuite extends Js2CpgCodeToCpgSuite {

  private val semanticsFilename: String =
    better.files.File(getClass.getResource("/default.semantics").toURI).pathAsString
  protected var semantics: Semantics  = _
  implicit var context: EngineContext = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    semantics = Semantics.fromList(new Parser().parseFile(semanticsFilename))
    context = EngineContext(semantics)
  }

  override def passes(cpg: Cpg): Unit = {
    val context = new LayerCreatorContext(cpg)
    new Base().run(context)
    new TypeRelations().run(context)
    new ControlFlow().run(context)
    new CallGraph().run(context)

    val options = new OssDataFlowOptions()
    new OssDataFlow(options).run(context)
  }

  protected def flowToResultPairs(path: Path): List[(String, Integer)] = {
    val pairs = path.elements.map {
      case point: MethodParameterIn =>
        val method      = point.method.head
        val method_name = method.name
        val code = s"$method_name(${method.parameter.l.sortBy(_.order).map(_.code).mkString(", ")})"
        (code, point.lineNumber.getOrElse(Int.box(-1)))
      case point =>
        (point.statement.repr, point.lineNumber.getOrElse(Int.box(-1)))
    }
    pairs.headOption
      .map(x => x :: pairs.sliding(2).collect { case Seq(a, b) if a != b => b }.toList)
      .getOrElse(List())
  }

}

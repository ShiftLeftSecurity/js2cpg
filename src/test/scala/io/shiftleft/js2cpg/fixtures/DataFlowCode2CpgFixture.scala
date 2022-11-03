package io.shiftleft.js2cpg.fixtures

import better.files.File
import io.joern.dataflowengineoss.language._
import io.joern.dataflowengineoss.layers.dataflows.OssDataFlow
import io.joern.dataflowengineoss.layers.dataflows.OssDataFlowOptions
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.joern.x2cpg.testfixtures.Code2CpgFixture
import io.joern.x2cpg.testfixtures.TestCpg
import io.joern.x2cpg.X2Cpg
import io.joern.x2cpg.passes.frontend.JavascriptCallLinker
import io.joern.x2cpg.testfixtures.LanguageFrontend
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.core.Js2Cpg
import io.shiftleft.semanticcpg.layers.LayerCreatorContext

trait Js2CpgFrontend extends LanguageFrontend {

  override val fileSuffix: String = ".js"

  override def execute(sourceCodePath: java.io.File): Cpg = {
    var cpg = Cpg.emptyCpg
    File.usingTemporaryFile("js2cpg", ".bin") { cpgFile =>
      val js2cpg = new Js2Cpg()
      val config = Config(
        sourceCodePath.getAbsolutePath,
        tsTranspiling = false,
        babelTranspiling = false,
        vueTranspiling = false,
        nuxtTranspiling = false,
        templateTranspiling = false,
        outputFile = cpgFile.pathAsString
      )
      js2cpg.run(config)
      cpg = Cpg.withConfig(overflowdb.Config.withDefaults.withStorageLocation(cpgFile.pathAsString))
    }
    cpg
  }
}

class DataFlowTestCpg extends TestCpg with Js2CpgFrontend {

  override def applyPasses(): Unit = {
    X2Cpg.applyDefaultOverlays(this)

    new JavascriptCallLinker(this).createAndApply()

    val context = new LayerCreatorContext(this)
    val options = new OssDataFlowOptions()
    new OssDataFlow(options).run(context)
  }

}

class DataFlowCode2CpgFixture extends Code2CpgFixture(() => new DataFlowTestCpg()) {

  implicit lazy val context: EngineContext = EngineContext()

  protected def flowToResultPairs(path: Path): List[(String, Integer)] =
    path.resultPairs().collect { case (firstElement: String, secondElement: Option[Integer]) =>
      (firstElement, secondElement.get)
    }
}

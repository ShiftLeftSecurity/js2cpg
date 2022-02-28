package io.shiftleft.js2cpg.dataflow

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.cpgloading.{CpgLoader, CpgLoaderConfig}
import io.shiftleft.js2cpg.core.{Config, Js2Cpg}
import io.shiftleft.semanticcpg.testfixtures.{CodeToCpgFixture, LanguageFrontend}

class Js2CpgFrontend(override val fileSuffix: String = ".js") extends LanguageFrontend() {
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
      cpg = CpgLoader
        .loadFromOverflowDb(
          CpgLoaderConfig.withDefaults
            .withOverflowConfig(overflowdb.Config.withDefaults.withStorageLocation(cpgFile.pathAsString))
        )
    }
    cpg
  }
}

class Js2CpgCodeToCpgSuite extends CodeToCpgFixture(new Js2CpgFrontend()) {}

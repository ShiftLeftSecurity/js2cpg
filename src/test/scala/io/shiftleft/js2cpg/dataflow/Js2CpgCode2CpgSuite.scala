package io.shiftleft.js2cpg.dataflow

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.cpgloading.{CpgLoader, CpgLoaderConfig}
import io.shiftleft.js2cpg.core.{Config, Js2Cpg}
import io.joern.x2cpg.testfixtures.{Code2CpgFixture, LanguageFrontend}

class Js2CpgFrontend extends LanguageFrontend {

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
      cpg = CpgLoader
        .loadFromOverflowDb(
          CpgLoaderConfig.withDefaults
            .withOverflowConfig(overflowdb.Config.withDefaults.withStorageLocation(cpgFile.pathAsString))
        )
    }
    cpg
  }
}

class Js2CpgCode2CpgSuite extends Code2CpgFixture(new Js2CpgFrontend())

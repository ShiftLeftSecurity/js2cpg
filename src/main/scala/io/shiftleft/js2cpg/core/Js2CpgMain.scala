package io.shiftleft.js2cpg.core

import Js2cpgArgumentsParser.*
import io.joern.x2cpg.X2CpgMain
import io.joern.x2cpg.utils.Environment

import java.nio.file.Paths

object Js2CpgMain extends X2CpgMain(parser, new Js2Cpg()) {
  def run(config: Config, js2cpg: Js2Cpg): Unit = {
    val absPath = Paths.get(config.inputPath).toAbsolutePath.toString
    if (Environment.pathExists(absPath)) {
      js2cpg.run(config.withInputPath(absPath))
    } else {
      System.exit(1)
    }
  }
}

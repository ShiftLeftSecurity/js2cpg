package io.shiftleft.js2cpg.core

object Js2CpgMain extends App {

  private val argumentsParser: Js2cpgArgumentsParser = new Js2cpgArgumentsParser()

  argumentsParser.parse(args) match {
    case Some(config) =>
      new Js2Cpg().run(config)
    case None =>
      argumentsParser.showUsage()
      System.exit(1)
  }

}

package io.shiftleft.js2cpg.core

object Js2CpgMain {
  def main(args: Array[String]): Unit = {
    val argumentsParser = new Js2cpgArgumentsParser()
    argumentsParser.parse(args) match {
      case Some(config) =>
        new Js2Cpg().run(config)
      case None =>
        argumentsParser.showUsage()
        System.exit(1)
    }

  }
}

package io.shiftleft.js2cpg.core

import io.joern.x2cpg.utils.server.FrontendHTTPServer

object Js2CpgMain {
  def main(args: Array[String]): Unit = {
    val argumentsParser = new Js2cpgArgumentsParser()
    argumentsParser.parse(args) match {
      case Some(config) if config.serverMode =>
        val server: FrontendHTTPServer = new FrontendHTTPServer(
          FrontendHTTPServer.defaultExecutor(),
          arguments => {
            val config = argumentsParser.parse(arguments)
            config.foreach(Js2Cpg().run(_))
          }
        )

        val port = server.startup()
        println(s"FrontendHTTPServer started on port $port")
        try {
          config.serverTimeoutSeconds match {
            case Some(value) => server.stopServerAfterTimeout(value)
            case None        => Thread.sleep(Long.MaxValue)
          }
        } finally {
          server.stop()
        }
      case Some(config) =>
        new Js2Cpg().run(config)
      case None =>
        argumentsParser.showUsage()
        System.exit(1)
    }

  }
}

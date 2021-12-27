package io.shiftleft.js2cpg.io

import java.io

import scala.collection.mutable
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Failure, Success, Try}

object ExternalCommand {

  def toOSCommand(command: String): String =
    if (scala.util.Properties.isWin) {
      command + ".cmd"
    } else { command }

  def run(command: String,
          inDir: String = ".",
          extraEnv: Map[String, String] = Map.empty): Try[String] = {
    val result                      = mutable.ArrayBuffer.empty[String]
    val lineHandler: String => Unit = line => result += line
    Process(command, new io.File(inDir), extraEnv.toList: _*)
      .!(ProcessLogger(lineHandler, lineHandler)) match {
      case 0 =>
        Success(result.mkString(System.lineSeparator()))
      case _ =>
        Failure(new RuntimeException(result.mkString(System.lineSeparator())))
    }
  }
}

package io.shiftleft.js2cpg.io

import java.util.concurrent.ConcurrentLinkedQueue
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters.CollectionHasAsScala
import org.apache.commons.lang3.StringUtils

object ExternalCommand {

  private val COMMAND_AND: String = " && "
  private val IS_WIN: Boolean     = scala.util.Properties.isWin

  def toOSCommand(command: String): String = if (IS_WIN) command + ".cmd" else command

  def run(command: String, inDir: String = ".", extraEnv: Map[String, String] = Map.empty): Try[String] = {
    val dir           = new java.io.File(inDir)
    val stdOutOutput  = new ConcurrentLinkedQueue[String]
    val stdErrOutput  = new ConcurrentLinkedQueue[String]
    val processLogger = ProcessLogger(stdOutOutput.add, stdErrOutput.add)
    val commands      = command.split(COMMAND_AND).toSeq
    commands.map { cmd =>
      val cmdWithQuotesAroundDir = StringUtils.replace(cmd, inDir, s"'$inDir'")
      Try(Process(cmdWithQuotesAroundDir, dir, extraEnv.toList *).!(processLogger)).getOrElse(1)
    }.sum match {
      case 0 =>
        Success(stdOutOutput.asScala.mkString(System.lineSeparator()))
      case _ =>
        val allOutput = stdOutOutput.asScala ++ stdErrOutput.asScala
        Failure(new RuntimeException(allOutput.mkString(System.lineSeparator())))
    }
  }

}

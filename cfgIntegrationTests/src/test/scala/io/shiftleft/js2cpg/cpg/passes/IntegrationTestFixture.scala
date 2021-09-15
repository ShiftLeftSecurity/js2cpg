package io.shiftleft.js2cpg.cpg.passes

import better.files.File
import overflowdb.Config
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.cpgloading.{CpgLoader, CpgLoaderConfig}

import java.io
import scala.collection.mutable
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Failure, Success, Try}

object IntegrationTestFixture {

  private val js2cpgPath = File(".") match {
    case f if f.canonicalPath.endsWith("cfgIntegrationTests") => f.parent
    case f                                                    => f
  }

  private object ExternalCommand {
    private val windowsSystemPrefix = "Windows"
    private val osNameProperty      = "os.name"

    def run(command: String): Try[String] = {
      val result = mutable.ArrayBuffer.empty[String]
      val lineHandler: String => Unit = line => {
        result.addOne(line)
      }

      val systemString = System.getProperty(osNameProperty)
      val (shellPrefix, cmd) =
        if (systemString != null && systemString.startsWith(windowsSystemPrefix)) {
          ("bash" :: Nil, command.replace("./", ""))
        } else {
          ("sh" :: "-c" :: Nil, command)
        }
      
      Process(shellPrefix :+ cmd, new io.File(js2cpgPath.pathAsString))
        .!(ProcessLogger(lineHandler, lineHandler)) match {
        case 0 =>
          Success(result.mkString(System.lineSeparator()))
        case _ =>
          Failure(new RuntimeException(result.mkString(System.lineSeparator())))
      }
    }
  }
}

abstract class IntegrationTestFixture {

  import IntegrationTestFixture.ExternalCommand

  protected def callFrontend(workspace: File, code: String): Try[Cpg] = {
    val file    = workspace / "test.js"
    val cpgPath = workspace / "cpg.bin.zip"
    file.write(code)

    ExternalCommand
      .run(
        s"./js2cpg.sh ${workspace.pathAsString} --output ${cpgPath.pathAsString} --no-ts --no-babel")
      .map { _ =>
        CpgLoader
          .loadFromOverflowDb(
            CpgLoaderConfig.withDefaults.withOverflowConfig(
              Config.withDefaults.withStorageLocation(cpgPath.pathAsString)))
      }
  }

}

package io.shiftleft.js2cpg.io

import better.files.File
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExternalCommandTest extends AnyWordSpec with Matchers {

  private val command = if (scala.util.Properties.isWin) "cmd /C dir" else "ls"

  "ExternalCommand" should {
    "run an external command with ProcessBuilder and no spaces in the directory name" in {
      File.usingTemporaryDirectory("js2cpgTest") { sourceDir =>
        val cmd = s"$command ${sourceDir.pathAsString}"
        (sourceDir / "Main.js").createFileIfNotExists().write("console.log('Foo');")
        ExternalCommand.run(cmd, sourceDir.pathAsString) should be a Symbol("success")
      }
    }

    "run an external command with ProcessBuilder and spaces in the directory name" in {
      File.usingTemporaryDirectory("js2cpg Test") { sourceDir =>
        val cmd = s"$command ${sourceDir.pathAsString}"
        (sourceDir / "Main.js").createFileIfNotExists().write("console.log('Foo');")
        ExternalCommand.run(cmd, sourceDir.pathAsString) should be a Symbol("success")
      }
    }
  }
}

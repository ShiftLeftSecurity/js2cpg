package io.shiftleft.js2cpg.io

import better.files.File
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExternalCommandTest extends AnyWordSpec with Matchers {
  "ExternalCommand" should {
    "run an external command with ProcessBuilder and no spaces in the directory name" in {
      File.usingTemporaryDirectory("js2cpgTest") { sourceDir =>
        val cmd = "ls " + sourceDir.pathAsString
        ExternalCommand.run(cmd, sourceDir.pathAsString) should be a Symbol("success")
      }
    }

    "run an external command with ProcessBuilder and spaces in the directory name" in {
      File.usingTemporaryDirectory("js2cpg Test") { sourceDir =>
        val cmd = "ls " + sourceDir.pathAsString
        ExternalCommand.run(cmd, sourceDir.pathAsString) should be a Symbol("success")
      }
    }
  }
}

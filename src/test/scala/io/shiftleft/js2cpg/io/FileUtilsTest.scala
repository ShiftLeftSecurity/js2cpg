package io.shiftleft.js2cpg.io

import better.files.File
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults.JS_SUFFIX

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FileUtilsTest extends AnyWordSpec with Matchers {

  "FileTree" should {
    "skip ignored files when copying" in {
      File.usingTemporaryDirectory("js2cpgTest") { sourceDir =>
        (sourceDir / "a.min.js").createFile()
        (sourceDir / "b-min.js").createFile()
        (sourceDir / "c.spec.js").createFile()
        (sourceDir / "d.chunk.js").createFile()
        (sourceDir / ".folder" / "e.js").createIfNotExists(createParents = true)

        File.usingTemporaryDirectory("js2cpgTest") { targetDir =>
          val config     = Config(srcDir = sourceDir.pathAsString)
          val copiedDir  = FileUtils.copyToDirectory(sourceDir, targetDir, config)
          val dirContent = FileUtils.getFileTree(copiedDir.path, config, List(JS_SUFFIX))
          dirContent shouldBe empty
        }
      }
    }

    "skip minified files" in {
      File.usingTemporaryDirectory("js2cpgTest") { sourceDir =>
        (sourceDir / "a.min.js").createFile()
        (sourceDir / "a.min.23472420.js").createFile()
        (sourceDir / "b-min.js").createFile()
        (sourceDir / "b-min.23472420.js").createFile()

        val config  = Config(srcDir = sourceDir.pathAsString)
        val minFile = (sourceDir / "something.js").createFile()
        minFile.write(s"console.log('${"x" * FileDefaults.LINE_LENGTH_THRESHOLD}');")
        val dirContent = FileUtils.getFileTree(sourceDir.path, config, List(JS_SUFFIX))
        dirContent shouldBe empty
      }
    }
  }

}

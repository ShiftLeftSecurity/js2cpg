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
          val copiedDir  = FileUtils.copyToDirectory(sourceDir, targetDir, Config())
          val dirContent = FileUtils.getFileTree(copiedDir.path, Config(), List(JS_SUFFIX))
          dirContent shouldBe empty
        }
      }
    }
  }

}

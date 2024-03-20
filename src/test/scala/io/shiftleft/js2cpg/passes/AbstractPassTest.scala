package io.shiftleft.js2cpg.passes

import better.files.File
import io.joern.x2cpg.X2Cpg.newEmptyCpg
import io.joern.x2cpg.utils.Report
import io.shiftleft.codepropertygraph.generated.nodes.Dependency
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.semanticcpg.language.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

abstract class AbstractPassTest extends AnyWordSpec with Matchers {

  protected abstract class Fixture

  protected def getDependencies(cpg: Cpg): Traversal[Dependency] = cpg.dependency

  protected object AstFixture extends Fixture {
    def apply(code: String)(f: Cpg => Unit): Unit = {
      File.usingTemporaryDirectory("js2cpgTest") { dir =>
        val file = dir / "code.js"
        file.write(code)
        val cpg       = newEmptyCpg()
        val filenames = List((file.path, file.parent.path))
        new AstCreationPass(cpg, filenames, Config().withInputPath(dir.toString), new Report()).createAndApply()
        f(cpg)
        file.delete()
      }
    }

    def apply(testFile: File)(f: Cpg => Unit): Unit = {
      val file      = testFile
      val cpg       = newEmptyCpg()
      val filenames = List((file.path, file.parent.path))
      new AstCreationPass(cpg, filenames, Config().withInputPath(file.parent.toString), new Report()).createAndApply()
      f(cpg)
    }

  }

}

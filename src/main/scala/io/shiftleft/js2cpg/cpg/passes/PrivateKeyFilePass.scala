package io.shiftleft.js2cpg.cpg.passes
import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.Report
import io.shiftleft.passes.IntervalKeyPool

import java.nio.file.Path
import scala.util.matching.Regex

class PrivateKeyFilePass(filenames: List[(Path, Path)],
                         cpg: Cpg,
                         keyPool: IntervalKeyPool,
                         report: Report)
    extends ConfigPass(filenames, cpg, keyPool, report) {

  private val PRIVATE_KEY: Regex = """.*RSA\sPRIVATE\sKEY.*""".r

  override def fileContent(filePath: Path): Iterable[String] =
    Iterable("Content omitted for security reasons.")

  override def partIterator: Iterator[(Path, Path)] =
    super.partIterator.filter(p => File(p._1).lineIterator.exists(PRIVATE_KEY.matches))

}

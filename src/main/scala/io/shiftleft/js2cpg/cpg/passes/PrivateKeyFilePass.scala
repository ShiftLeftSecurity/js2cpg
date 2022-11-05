package io.shiftleft.js2cpg.cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.Report
import io.shiftleft.js2cpg.io.FileUtils

import java.nio.file.Path
import scala.util.matching.Regex

class PrivateKeyFilePass(filenames: List[(Path, Path)], cpg: Cpg, report: Report)
    extends ConfigPass(filenames, cpg, report) {

  private val PRIVATE_KEY: Regex = """.*RSA\sPRIVATE\sKEY.*""".r

  override def fileContent(filePath: Path): Iterable[String] =
    Iterable("Content omitted for security reasons.")

  override def generateParts(): Array[(Path, Path)] =
    super.generateParts().filter(p => FileUtils.readLinesInFile(p._1).exists(PRIVATE_KEY.matches))

}

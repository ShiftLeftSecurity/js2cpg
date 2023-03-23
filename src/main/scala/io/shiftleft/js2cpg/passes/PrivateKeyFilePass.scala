package io.shiftleft.js2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.js2cpg.core.Report
import io.shiftleft.utils.IOUtils

import java.nio.file.Path
import scala.util.matching.Regex

class PrivateKeyFilePass(filenames: List[(Path, Path)], cpg: Cpg, report: Report)
    extends ConfigPass(filenames, cpg, report) {

  private val PRIVATE_KEY: Regex = """.*RSA\sPRIVATE\sKEY.*""".r

  override def fileContent(filePath: Path): Seq[String] = Seq("Content omitted for security reasons.")

  override def generateParts(): Array[(Path, Path)] =
    super.generateParts().filter(p => IOUtils.readLinesInFile(p._1).exists(PRIVATE_KEY.matches))

}

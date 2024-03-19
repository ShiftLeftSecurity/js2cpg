package io.shiftleft.js2cpg.utils

import better.files.File
import com.oracle.js.parser.Source
import java.nio.file.{Path, Paths}
import io.shiftleft.js2cpg.parser.JsSource

object SourceWrapper {
  implicit class SourceWrapper(val source: Source) extends AnyVal {
    def toJsSource(srcDir: File = File(""), projectDir: Path = Paths.get("")) = new JsSource(srcDir, projectDir, source)
  }
}

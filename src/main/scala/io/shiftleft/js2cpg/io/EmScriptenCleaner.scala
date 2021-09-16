package io.shiftleft.js2cpg.io

import io.shiftleft.js2cpg.io.FileDefaults.{EMSCRIPTEN_END_FUNCS, EMSCRIPTEN_START_FUNCS}

object EmScriptenCleaner {

  /**
    * If code contains emscripten code (marked with start funcs and end funcs comments)
    * we simply replace it with empty lines.
    */
  def clean(code: Iterator[String]): Iterator[String] = {
    val lines      = code.toSeq
    val startIndex = lines.indexWhere(EMSCRIPTEN_START_FUNCS.matches)
    val endIndex   = lines.indexWhere(EMSCRIPTEN_END_FUNCS.matches)
    if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
      (lines.slice(0, startIndex) ++
        Seq.fill(endIndex - startIndex - 1)(System.lineSeparator()) ++
        lines.slice(endIndex + 1, lines.length)).iterator
    } else {
      lines.iterator
    }
  }

}

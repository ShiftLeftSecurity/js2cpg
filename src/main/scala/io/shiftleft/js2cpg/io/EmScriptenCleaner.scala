package io.shiftleft.js2cpg.io

import io.shiftleft.js2cpg.io.FileDefaults.{EMSCRIPTEN_END_FUNCS, EMSCRIPTEN_START_FUNCS}

object EmScriptenCleaner {

  /**
    * If code contains emscripten code (marked with start funcs and end funcs comments)
    * we simply replace it with empty lines.
    */
  def clean(code: Seq[String]): Iterator[String] = {
    val startIndex = code.indexWhere(EMSCRIPTEN_START_FUNCS.matches)
    val endIndex   = code.indexWhere(EMSCRIPTEN_END_FUNCS.matches)
    if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
      (code.slice(0, startIndex) ++
        Seq.fill(endIndex - startIndex - 1)(System.lineSeparator()) ++
        code.slice(endIndex + 1, code.length)).iterator
    } else {
      code.iterator
    }
  }

}

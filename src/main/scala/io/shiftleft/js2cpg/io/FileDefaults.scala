package io.shiftleft.js2cpg.io

import scala.util.matching.Regex

object FileDefaults {

  val NODE_MODULES_DIR_NAME: String = "node_modules"

  val PRIVATE_MODULES_DIR_NAME: String = "sl_private"

  val WEBPACK_PREFIX: String = if (scala.util.Properties.isWin) "webpack://" else "webpack:/"

  val JS_SUFFIX: String = ".js"

  val MJS_SUFFIX: String = ".mjs"

  val VUE_SUFFIX: String = ".vue"

  val HTML_SUFFIX: String = ".html"

  val KEY_SUFFIX: String = ".key"

  val PUG_SUFFIX: String = ".pug"

  val EJS_SUFFIX: String = ".ejs"

  val TS_SUFFIX: String = ".ts"

  val DTS_SUFFIX: String = ".d.ts"

  val VSIX_SUFFIX: String = ".vsix"

  val NPMRC_NAME: String = ".npmrc"

  val CONFIG_FILES: List[String] = List(".json", ".config.js", ".conf.js")

  val NUM_LINES_THRESHOLD: Long = 10000

  val EMSCRIPTEN_START_FUNCS: Regex = "// EMSCRIPTEN_START_FUNCS.*".r
  val EMSCRIPTEN_END_FUNCS: Regex   = "// EMSCRIPTEN_END_FUNCS.*".r

  val BUILD_PATH_REGEX: Regex = ".*(www|dist|build|vendor).*\\.js".r

  val IGNORED_FILES_REGEX: Seq[Regex] = List(
    ".*jest\\.config.*".r,
    ".*webpack\\..*\\.js".r,
    ".*vue\\.config\\.js".r,
    ".*babel\\.config\\.js".r,
    ".*chunk-vendors.*\\.js".r, // commonly found in webpack / vue.js projects
    ".*app~.*\\.js".r, // commonly found in webpack / vue.js projects
    ".*\\.chunk\\.js".r, // see: https://github.com/ShiftLeftSecurity/product/issues/8197
    ".*\\.babelrc.*".r,
    ".*\\.eslint.*".r,
    ".*\\.tslint.*".r,
    ".*\\.stylelintrc\\.js".r,
    ".*rollup\\.config.*".r,
    ".*\\.types\\.js".r,
    ".*\\.cjs\\.js".r
  )

  val IGNORED_TESTS_REGEX: Seq[Regex] = List(
    ".*\\.spec\\.js".r,
    ".*\\.mock\\.js".r,
    ".*\\.e2e\\.js".r,
    ".*\\.test\\.js".r
  )

  val IGNORED_FOLDERS_REGEX: Seq[Regex] = List(
    "__.*__".r,
    "\\..*".r,
    "jest-cache".r,
    (NODE_MODULES_DIR_NAME + ".*").r
  )

  val MINIFIED_PATH_REGEX: Regex = ".*([.-]min\\.js|bundle\\.js)".r

}

package io.shiftleft.js2cpg.io

import scala.util.matching.Regex

object FileDefaults {

  val NUM_LINES_THRESHOLD: Int   = 10000
  val LINE_LENGTH_THRESHOLD: Int = 10000

  val NODE_MODULES_DIR_NAME: String    = "node_modules"
  val PRIVATE_MODULES_DIR_NAME: String = "sl_private"
  val WEBPACK_PREFIX: String           = "webpack://"
  val REGISTRY_MARKER                  = ":registry="
  val JS_SUFFIX: String                = ".js"
  val MJS_SUFFIX: String               = ".mjs"
  val VUE_SUFFIX: String               = ".vue"
  val HTML_SUFFIX: String              = ".html"
  val KEY_SUFFIX: String               = ".key"
  val PUG_SUFFIX: String               = ".pug"
  val EJS_SUFFIX: String               = ".ejs"
  val TS_SUFFIX: String                = ".ts"
  val DTS_SUFFIX: String               = ".d.ts"
  val VSIX_SUFFIX: String              = ".vsix"
  val NPMRC_NAME: String               = ".npmrc"
  val YARNRC_NAME: String              = ".yarnrc"

  val EMSCRIPTEN_START_FUNCS: Regex = "// EMSCRIPTEN_START_FUNCS.*".r
  val EMSCRIPTEN_END_FUNCS: Regex   = "// EMSCRIPTEN_END_FUNCS.*".r

  val IGNORED_FILES_REGEX: Seq[Regex] = List(
    ".*jest\\.config.*".r,
    ".*webpack\\..*\\.js".r,
    ".*vue\\.config\\.js".r,
    ".*babel\\.config\\.js".r,
    ".*chunk-vendors.*\\.js".r, // commonly found in webpack / vue.js projects
    ".*app~.*\\.js".r,          // commonly found in webpack / vue.js projects
    ".*app-legacy\\.js".r,      // commonly found in webpack / vue.js projects
    ".*\\.chunk\\.js".r,        // see: https://github.com/ShiftLeftSecurity/product/issues/8197
    ".*\\.babelrc.*".r,
    ".*\\.eslint.*".r,
    ".*\\.tslint.*".r,
    ".*\\.stylelintrc\\.js".r,
    ".*rollup\\.config.*".r,
    ".*\\.types\\.(js|tsx|ts)".r,
    ".*\\.cjs\\.js".r,
    ".*eslint-local-rules\\.js".r
  )

  val IGNORED_TESTS_REGEX: Seq[Regex] = List(
    ".*[.-]spec\\.(js|tsx|ts)".r,
    ".*[.-]mock\\.(js|tsx|ts)".r,
    ".*[.-]e2e\\.(js|tsx|ts)".r,
    ".*[.-]test\\.(js|tsx|ts)".r
  )

  val IGNORED_FOLDERS_REGEX: Seq[Regex] = List(
    "__.*__".r,
    "\\..*".r,
    "jest-cache".r,
    "codemods".r,
    "e2e".r,
    "e2e-beta".r,
    "eslint-rules".r,
    "flow-typed".r,
    "i18n".r,
    "vendor".r,
    "www".r,
    "dist".r,
    "build".r,
    (NODE_MODULES_DIR_NAME + ".*").r
  )

  val MINIFIED_PATH_REGEX: Regex = ".*([.-]min\\..*js|bundle\\.js)".r

  val PACKAGE_JSON_FILENAME: String  = "package.json"
  val JSON_LOCK_FILENAME: String     = "package-lock.json"
  val PNPM_LOCK_FILENAME: String     = "pnpm-lock.yaml"
  val PNPM_LOCK_FILENAME_BAK: String = "pnpm-lock.yaml.bak"
  val YARN_LOCK_FILENAME: String     = "yarn.lock"
  val YARN_LOCK_FILENAME_BAK: String = "yarn.lock.bak"

  private val NPM_SHRINKWRAP_FILENAME: String = "npm-shrinkwrap.json"
  private val PNPM_WS_FILENAME: String        = "pnpm-workspace.yaml"
  private val WEBPACK_CONFIG_FILENAME: String = "webpack.config.js"
  private val ES_LINT_RC_FILENAME: String     = ".eslintrc.js"
  private val ANGULAR_JSON_FILENAME: String   = "angular.json"

  val PROJECT_CONFIG_FILES: List[String] = List(
    JSON_LOCK_FILENAME,
    YARN_LOCK_FILENAME,
    PNPM_LOCK_FILENAME,
    // pnpm workspace config file is not required as we manually descent into subprojects:
    PNPM_WS_FILENAME,
    NPM_SHRINKWRAP_FILENAME,
    WEBPACK_CONFIG_FILENAME,
    ANGULAR_JSON_FILENAME,
    ES_LINT_RC_FILENAME,
    NPMRC_NAME,
    YARNRC_NAME
  )

  val CONFIG_FILES: List[String] = List(".json", ".config.js", ".conf.js")

}

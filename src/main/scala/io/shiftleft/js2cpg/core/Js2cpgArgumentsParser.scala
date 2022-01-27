package io.shiftleft.js2cpg.core

import io.shiftleft.js2cpg.io.FileDefaults
import io.shiftleft.js2cpg.io.FileDefaults.VSIX_SUFFIX

import java.io.File
import io.shiftleft.js2cpg.parser.PackageJsonParser
import io.shiftleft.js2cpg.preprocessing.TypescriptTranspiler
import scopt.OptionParser

object Js2cpgArgumentsParser {
  val HELP: String                     = "help"
  val VERSION: String                  = "version"
  val SRCDIR: String                   = "<src>"
  val OUTPUT: String                   = "output"
  val WITH_TS_TYPES: String            = "with-typescript-types"
  val EXCLUDE: String                  = "exclude"
  val EXCLUDE_REGEX: String            = "exclude-regex"
  val PACKAGE_JSON: String             = "package-json"
  val NO_TS: String                    = "no-ts"
  val TS: String                       = "ts"
  val NO_BABEL: String                 = "no-babel"
  val NO_VUE: String                   = "no-vue-js"
  val NO_NUXT: String                  = "no-nuxt-js"
  val NO_TEMPLATES: String             = "no-templates"
  val BABEL: String                    = "babel"
  val TRANSPILING: String              = "transpiling"
  val IGNORE_MINIFIED: String          = "ignore-minified"
  val WITH_MINIFIED: String            = "with-minified"
  val INCLUDE_MINIFIED: String         = "include-minified"
  val WITH_TESTS: String               = "with-tests"
  val INCLUDE_TESTS: String            = "include-tests"
  val IGNORE_PRIVATE_DEPS: String      = "ignore-private-deps"
  val EXCLUDE_PRIVATE_DEPS: String     = "exclude-private-deps"
  val PRIVATE_DEPS: String             = "private-deps-ns"
  val INCLUDE_CONFIGS: String          = "include-configs"
  val INCLUDE_HTML: String             = "include-html"
  val JVM_MONITOR: String              = "enable-jvm-monitor"
  val MODULE_MODE: String              = "module-mode"
  val WITH_NODE_MODULES_FOLDER: String = "with-node-modules-folder"
}

class Js2cpgArgumentsParser {

  import Js2cpgArgumentsParser._

  private lazy val banner: String =
    """
       |     ██╗███████╗██████╗  ██████╗██████╗  ██████╗
       |     ██║██╔════╝╚════██╗██╔════╝██╔══██╗██╔════╝
       |     ██║███████╗ █████╔╝██║     ██████╔╝██║  ███╗
       |██   ██║╚════██║██╔═══╝ ██║     ██╔═══╝ ██║   ██║
       |╚█████╔╝███████║███████╗╚██████╗██║     ╚██████╔╝
       | ╚════╝ ╚══════╝╚══════╝ ╚═════╝╚═╝      ╚═════╝
     """.stripMargin

  private val parser: OptionParser[Config] = new OptionParser[Config]("js2cpg.sh") {
    help(HELP).text("prints this usage text")
    head(s"""
            |$banner
            |js2cpg version "${io.shiftleft.js2cpg.core.BuildInfo.version}"
            |""".stripMargin)
    version(VERSION)
      .text("print js2cpg version and exit")
    arg[String](SRCDIR)
      .required()
      .text("directory containing Javascript code or the path to a *.vsix file")
      .action((x, c) => c.copy(srcDir = x).withLoadedIgnores())
      .validate(path => {
        val f = new File(path)
        if (f.exists() && (f.isDirectory || f.toString.endsWith(VSIX_SUFFIX))) success
        else failure(s"Invalid $SRCDIR path: '$path'")
      })
    opt[String](PACKAGE_JSON)
      .text(
        s"path to the projects package.json (path relative to $SRCDIR or absolute path; defaults to '${SRCDIR + java.io.File.separator + PackageJsonParser.PACKAGE_JSON_FILENAME}')"
      )
      .action((x, c) => c.copy(packageJsonLocation = x))
      .validate(path => {
        val f = new File(path)
        if (f.exists() && !f.isDirectory) success
        else failure(s"File '$path' does not exist or is a directory")
      })
    opt[String](OUTPUT)
      .text(s"CPG output file name (defaults to `${Config.DEFAULT_CPG_OUT_FILE}`)")
      .action((x, c) => c.copy(outputFile = x))
      .validate(x =>
        if (x.isEmpty) {
          failure("Output file cannot be empty")
        } else if (!new File(x).getAbsoluteFile.getParentFile.exists()) {
          failure("Directory of the output file does not exist")
        } else success
      )
    opt[Unit](NO_TS)
      .text("disables transpiling Typescript files to Javascript")
      .action((_, c) => c.copy(tsTranspiling = false))
    opt[Unit](NO_BABEL)
      .text("disables transpiling Javascript files with Babel")
      .action((_, c) => c.copy(babelTranspiling = false))
    opt[Unit](NO_VUE)
      .text("disables transpiling Vue.js files")
      .action((_, c) => c.copy(vueTranspiling = false))
    opt[Unit](NO_NUXT)
      .text("disables Nuxt.js transpiling")
      .action((_, c) => c.copy(nuxtTranspiling = false))
    opt[Unit](NO_TEMPLATES)
      .text("disables transpiling EJS or Pug template files")
      .action((_, c) => c.copy(templateTranspiling = false))
    // for backwards compatibility - has no effect:
    opt[Unit](TRANSPILING)
      .text("enables transpiling Typescript files to Javascript")
      .hidden() // deprecated
    // for backwards compatibility - has no effect:
    opt[Unit](BABEL)
      .text("enables transpiling Javascript files with Babel")
      .hidden()
    // for backwards compatibility - has no effect:
    opt[Unit](TS)
      .text("enables transpiling Typescript files to Javascript")
      .hidden()
    opt[Unit](WITH_NODE_MODULES_FOLDER)
      .text(
        s"include the node_module folder (defaults to `${Config.DEFAULT_WITH_NODE_MODULES_FOLDER}`)"
      )
      .action((_, c) => c.copy(withNodeModuleFolder = true))
      .hidden()
    opt[Unit](WITH_TS_TYPES)
      .text(
        s"query types via Typescript; needs a `package.json` (defaults to `${Config.DEFAULT_TS_TYPES}`)"
      )
      .action((_, c) => c.copy(withTsTypes = true))
      .hidden() // deprecated
    opt[Seq[String]](EXCLUDE)
      .valueName("<file1>,<file2>,...")
      .action((x, c) => c.copy(ignoredFiles = c.ignoredFiles ++ x.map(c.createPathForIgnore)))
      .text("files to exclude during CPG generation (paths relative to <srcdir> or absolute paths)")
    opt[String](EXCLUDE_REGEX)
      .action((x, c) => c.copy(ignoredFilesRegex = x.r))
      .text(
        "a regex specifying files to exclude during CPG generation (the absolute file path is matched)"
      )
    // for backwards compatibility - has no effect:
    opt[Unit](IGNORE_MINIFIED)
      .text(
        "ignore minified Javascript files (filename ending with '-min.js', '.min.js', or 'bundle.js')"
      )
      .hidden() // deprecated
    opt[Unit](WITH_MINIFIED)
      .action((_, c) => c.copy(ignoreMinified = false))
      .hidden() // deprecated
      .text(
        "include minified Javascript files (filename ending with '-min.js', '.min.js', or 'bundle.js')"
      )
    opt[Unit](INCLUDE_MINIFIED)
      .action((_, c) => c.copy(ignoreMinified = false))
      .text(
        "include minified Javascript files (filename ending with '-min.js', '.min.js', or 'bundle.js')"
      )
    opt[Unit](WITH_TESTS)
      .action((_, c) => c.copy(ignoreTests = false))
      .hidden() // deprecated
      .text("include test files")
    opt[Unit](INCLUDE_TESTS)
      .action((_, c) => c.copy(ignoreTests = false))
      .text("include test files")
    opt[Unit](IGNORE_PRIVATE_DEPS)
      .text(
        s"ignores private modules/dependencies in 'node_modules/' (defaults to `${Config.DEFAULT_IGNORE_PRIVATE_DEPS}`)"
      )
      .action((_, c) => c.copy(ignorePrivateDeps = true))
      .hidden()
    opt[Unit](EXCLUDE_PRIVATE_DEPS)
      .text(
        s"excludes private modules/dependencies in 'node_modules/' (defaults to `${Config.DEFAULT_IGNORE_PRIVATE_DEPS}`)"
      )
      .action((_, c) => c.copy(ignorePrivateDeps = true))
    opt[Seq[String]](PRIVATE_DEPS)
      .valueName("<dep1>,<dep2>,...")
      .action((x, c) => c.copy(privateDeps = c.privateDeps ++ x.flatMap(d => Seq(d, s"@$d"))))
      .text(
        s"additional private dependencies to be analyzed from '${FileDefaults.NODE_MODULES_DIR_NAME}'"
      )
    opt[Unit](INCLUDE_CONFIGS)
      .text("include configuration files (*.conf.js, *.config.js, *.json)")
      .action((_, c) => c.copy(includeConfigs = true))
    opt[Unit](INCLUDE_HTML)
      .text("include HTML files (*.html)")
      .action((_, c) => c.copy(includeHtml = true))
    opt[Int](JVM_MONITOR)
      .text("enable JVM metrics logging (requires JMX port number)")
      .action((jmxPortNumber, c) => c.copy(jvmMetrics = Some(jmxPortNumber)))
      .hidden()
    opt[String](MODULE_MODE)
      .text(
        s"set the module mode for transpiling (default is ${TypescriptTranspiler.DEFAULT_MODULE}, alternatives are e.g., esnext or es2015)"
      )
      .action((module, c) => c.copy(moduleMode = Some(module)))
      .hidden()
  }

  def parse(args: Array[String]): Option[Config] = parser.parse(args, Config())

  def showUsage(): Unit = println(parser.usage)

}

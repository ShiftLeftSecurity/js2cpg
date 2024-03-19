package io.shiftleft.js2cpg.core

import io.shiftleft.js2cpg.io.FileDefaults
import java.io.File
import io.shiftleft.js2cpg.preprocessing.TypescriptTranspiler
import scopt.OParser

object Js2cpgArgumentsParser {
  implicit val defaultConfig: Config           = Config()
  val VERSION: String                          = "version"
  val WITH_TS_TYPES: String                    = "with-typescript-types"
  val PACKAGE_JSON: String                     = "package-json"
  val NO_TS: String                            = "no-ts"
  val TS: String                               = "ts"
  val NO_BABEL: String                         = "no-babel"
  val NO_VUE: String                           = "no-vue-js"
  val NO_NUXT: String                          = "no-nuxt-js"
  val NO_TEMPLATES: String                     = "no-templates"
  val BABEL: String                            = "babel"
  val TRANSPILING: String                      = "transpiling"
  val IGNORE_MINIFIED: String                  = "ignore-minified"
  val WITH_MINIFIED: String                    = "with-minified"
  val INCLUDE_MINIFIED: String                 = "include-minified"
  val WITH_TESTS: String                       = "with-tests"
  val INCLUDE_TESTS: String                    = "include-tests"
  val IGNORE_PRIVATE_DEPS: String              = "ignore-private-deps"
  val EXCLUDE_PRIVATE_DEPS: String             = "exclude-private-deps"
  val PRIVATE_DEPS: String                     = "private-deps-ns"
  val INCLUDE_CONFIGS: String                  = "include-configs"
  val INCLUDE_HTML: String                     = "include-html"
  val EXCLUDE_HTML: String                     = "exclude-html"
  val JVM_MONITOR: String                      = "enable-jvm-monitor"
  val MODULE_MODE: String                      = "module-mode"
  val WITH_NODE_MODULES_FOLDER: String         = "with-node-modules-folder"
  val OPTIMIZE_DEPENDENCIES: String            = "optimize-dependencies"
  val ALL_DEPENDENCIES: String                 = "all-dependencies"
  val FIXED_TRANSPILATION_DEPENDENCIES: String = "fixed-transpilation-dependencies"

  private lazy val banner: String =
    """
       |     ██╗███████╗██████╗  ██████╗██████╗  ██████╗
       |     ██║██╔════╝╚════██╗██╔════╝██╔══██╗██╔════╝
       |     ██║███████╗ █████╔╝██║     ██████╔╝██║  ███╗
       |██   ██║╚════██║██╔═══╝ ██║     ██╔═══╝ ██║   ██║
       |╚█████╔╝███████║███████╗╚██████╗██║     ╚██████╔╝
       | ╚════╝ ╚══════╝╚══════╝ ╚═════╝╚═╝      ╚═════╝
     """.stripMargin

  val parser: OParser[Unit, Config] = {
    val builder = OParser.builder[Config]
    import builder.*
    OParser.sequence(
      programName("js2cpg"),
      head(s"""
            |$banner
            |js2cpg version "${io.shiftleft.js2cpg.core.BuildInfo.version}"
            |""".stripMargin),
      version(VERSION)
        .text("print js2cpg version and exit"),
      opt[String](PACKAGE_JSON)
        .text(
          s"path to the projects package.json (path relative to <input-dir> or absolute path; defaults to '<input-dir>${java.io.File.separator}${FileDefaults.PACKAGE_JSON_FILENAME}')"
        )
        .action((x, c) => c.withPackageJsonLocation(x))
        .validate(path => {
          val f = new File(path)
          if (f.exists() && !f.isDirectory) success
          else failure(s"File '$path' does not exist or is a directory")
        }),
      opt[Unit](NO_TS)
        .text("disables transpiling Typescript files to Javascript")
        .action((_, c) => c.withTsTranspiling(false)),
      opt[Unit](NO_BABEL)
        .text("disables transpiling Javascript files with Babel")
        .action((_, c) => c.withBabelTranspiling(false)),
      opt[Unit](NO_VUE)
        .text("disables transpiling Vue.js files")
        .action((_, c) => c.withVueTranspiling(false)),
      opt[Unit](NO_NUXT)
        .text("disables Nuxt.js transpiling")
        .action((_, c) => c.withNuxtTranspiling(false)),
      opt[Unit](NO_TEMPLATES)
        .text("disables transpiling EJS or Pug template files")
        .action((_, c) => c.withTemplateTranspiling(false)),
      // for backwards compatibility - has no effect:
      opt[Unit](TRANSPILING)
        .text("enables transpiling Typescript files to Javascript")
        .hidden(), // deprecated
      // for backwards compatibility - has no effect:
      opt[Unit](BABEL)
        .text("enables transpiling Javascript files with Babel")
        .hidden(),
      // for backwards compatibility - has no effect:
      opt[Unit](TS)
        .text("enables transpiling Typescript files to Javascript")
        .hidden(),
      opt[Unit](WITH_NODE_MODULES_FOLDER)
        .text(s"include the node_module folder (defaults to '${Config.DEFAULT_WITH_NODE_MODULES_FOLDER}')")
        .action((_, c) => c.withNodeModuleFolder(true))
        .hidden(),
      opt[Unit](WITH_TS_TYPES)
        .text(s"query types via Typescript; needs a `package.json` (defaults to '${Config.DEFAULT_TS_TYPES}')")
        .action((_, c) => c.withTsTypes(true))
        .hidden(), // deprecated
      // for backwards compatibility - has no effect:
      opt[Unit](IGNORE_MINIFIED)
        .text("ignore minified Javascript files (filename ending with '-min.js', '.min.js', or 'bundle.js')")
        .hidden(), // deprecated
      opt[Unit](WITH_MINIFIED)
        .action((_, c) => c.withIgnoreMinified(false))
        .text("include minified Javascript files (filename ending with '-min.js', '.min.js', or 'bundle.js')")
        .hidden(), // deprecated
      opt[Unit](INCLUDE_MINIFIED)
        .action((_, c) => c.withIgnoreMinified(false))
        .text("include minified Javascript files (filename ending with '-min.js', '.min.js', or 'bundle.js')"),
      opt[Unit](WITH_TESTS)
        .action((_, c) => c.withIgnoreTests(false))
        .text("include test files")
        .hidden(), // deprecated
      opt[Unit](INCLUDE_TESTS)
        .action((_, c) => c.withIgnoreTests(false))
        .text("include test files"),
      opt[Unit](IGNORE_PRIVATE_DEPS)
        .text(
          s"ignores private modules/dependencies in 'node_modules/' (defaults to '${Config.DEFAULT_IGNORE_PRIVATE_DEPS}')"
        )
        .action((_, c) => c.withIgnorePrivateDeps(true))
        .hidden(),
      opt[Unit](EXCLUDE_PRIVATE_DEPS)
        .text(
          s"excludes private modules/dependencies in 'node_modules/' (defaults to '${Config.DEFAULT_IGNORE_PRIVATE_DEPS}')"
        )
        .action((_, c) => c.withIgnorePrivateDeps(true)),
      opt[Seq[String]](PRIVATE_DEPS)
        .valueName("<dep1>,<dep2>,...")
        .action((x, c) => c.withPrivateDeps(c.privateDeps ++ x.flatMap(d => Seq(d, s"@$d"))))
        .text(s"additional private dependencies to be analyzed from '${FileDefaults.NODE_MODULES_DIR_NAME}/'"),
      opt[Unit](INCLUDE_CONFIGS)
        .text("include configuration files (*.conf.js, *.config.js, *.json)")
        .hidden(), // deprecated, it is the default
      opt[Unit](INCLUDE_HTML)
        .text("include HTML files (*.html)")
        .hidden(), // deprecated, it is the default
      opt[Unit](EXCLUDE_HTML)
        .text("excludes HTML files (*.html)")
        .action((_, c) => c.withIncludeHtml(false)),
      opt[Unit](OPTIMIZE_DEPENDENCIES)
        .text(
          s"optimize project dependencies during transpilation (defaults to '${Config.DEFAULT_OPTIMIZE_DEPENDENCIES}')"
        )
        .hidden(), // deprecated, it is the default
      opt[Unit](ALL_DEPENDENCIES)
        .text(
          s"install all project dependencies during transpilation (defaults to '${!Config.DEFAULT_OPTIMIZE_DEPENDENCIES}')"
        )
        .action((_, c) => c.withOptimizeDependencies(false)),
      opt[Unit](FIXED_TRANSPILATION_DEPENDENCIES)
        .text(
          s"install fixed versions of transpilation dependencies during transpilation (defaults to '${!Config.DEFAULT_FIXED_TRANSPILATION_DEPENDENCIES}')"
        )
        .action((_, c) => c.withFixedTranspilationDependencies(true)),
      opt[Int](JVM_MONITOR)
        .text("enable JVM metrics logging (requires JMX port number)")
        .action((jmxPortNumber, c) => c.withJvmMetrics(Some(jmxPortNumber)))
        .hidden(),
      opt[String](MODULE_MODE)
        .text(
          s"set the module mode for transpiling (default is '${TypescriptTranspiler.DefaultModule}', alternatives are e.g., esnext or es2015)"
        )
        .action((module, c) => c.withModuleMode(Some(module)))
        .hidden()
    )
  }

}

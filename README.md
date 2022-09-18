[![Release](https://github.com/ShiftLeftSecurity/js2cpg/actions/workflows/release.yml/badge.svg)](https://github.com/ShiftLeftSecurity/js2cpg/actions/workflows/release.yml)

# js2cpg Frontend

This is a [CPG](https://docs.joern.io/code-property-graph/) frontend based on Javascript source code powered by the [GraalJS Parser](https://github.com/oracle/graaljs/tree/master/graal-js/src/com.oracle.js.parser) which is part of the [GraalVM JS project](https://www.graalvm.org/reference-manual/js/).

## Setup

Requirements:
- \>= JDK 8. We recommend OpenJDK 8 or AdoptJDK 8.
- sbt (https://www.scala-sbt.org/)

## Installation

You can build `js2cpg` by running the command below.

``` bash
sbt stage
```

After running `js2cpg` by invoking `./js2cpg.sh` you should be able to see the output below.

``` bash
Error: Missing argument <srcdir>
Try --help for more information.


     ██╗███████╗██████╗  ██████╗██████╗  ██████╗
     ██║██╔════╝╚════██╗██╔════╝██╔══██╗██╔════╝
     ██║███████╗ █████╔╝██║     ██████╔╝██║  ███╗
██   ██║╚════██║██╔═══╝ ██║     ██╔═══╝ ██║   ██║
╚█████╔╝███████║███████╗╚██████╗██║     ╚██████╔╝
 ╚════╝ ╚══════╝╚══════╝ ╚═════╝╚═╝      ╚═════╝
     
js2cpg version "current version number"

Usage: js2cpg.sh [options] <srcdir>

  --help                   prints this usage text
  --version                print js2cpg version and exit
  <src>                    directory containing Javascript code or the path to a *.vsix file
  --package-json <value>   path to the projects package.json (path relative to <src> or absolute path; defaults to '<src>\package.json')
  --output <value>         CPG output file name (defaults to 'cpg.bin.zip')
  --no-ts                  disables transpiling Typescript files to Javascript
  --no-babel               disables transpiling Javascript files with Babel
  --no-vue-js              disables transpiling Vue.js files
  --no-nuxt-js             disables Nuxt.js transpiling
  --no-templates           disables transpiling EJS or Pug template files
  --exclude <file1>,<file2>,...
                           files to exclude during CPG generation (paths relative to <srcdir> or absolute paths)
  --exclude-regex <value>  a regex specifying files to exclude during CPG generation (the absolute file path is matched)
  --include-minified       include minified Javascript files (filename ending with '-min.js', '.min.js', or 'bundle.js')
  --include-tests          include test files
  --exclude-private-deps   excludes private modules/dependencies in 'node_modules/' (defaults to 'false')
  --private-deps-ns <dep1>,<dep2>,...
                           additional private dependencies to be analyzed from 'node_modules/'
  --include-configs        include configuration files (*.conf.js, *.config.js, *.json)
  --exclude-html           excludes HTML files (*.html)
  --all-dependencies       install all project dependencies during transpilation (defaults to 'false')
```

`js2cpg` requires at least one argument `<srcdir>`. `srcdir` is path to the project directory from which you would like to generate a CPG.
The option `output` parameter describes the location in the file system where the CPG should be stored to.

## Quickstart

1. Clone the project
2. Build the project `sbt stage`
3. Create a CPG `./js2cpg.sh /path/to/your/code -o /path/to/cpg.bin`
4. Download [Joern](https://github.com/joernio/joern) with
   ```
   wget https://github.com/joernio/joern/releases/latest/download/joern-cli.zip
   unzip joern-cli.zip
   cd joern-cli
   ```
5. Copy `cpg.bin` into the Joern directory
6. Start Joern with `./joern.sh`
7. Import the cpg with `importCpg("cpg.bin")`
8. Now you can query the CPG 

# Overview

`js2cpg` operates in three major steps: Preprocessing, parsing, and CPG-generation which are explained below.

## Preprocessing

This runs our [transpilers/preprocessors](https://github.com/ShiftLeftSecurity/js2cpg/tree/master/src/main/scala/io/shiftleft/js2cpg/preprocessing) if the input project contains at least one element of the targeted language extension or template language (e.g., at least one Typescript file).

  - Babel
  - EJS
  - Nuxt.js
  - PUG templates
  - Vue.js templates
  - Typescript

With this, we ensure to have ES6 compliant JS code before we continue with the actual parsing and CPG-generation.

## Parsing

This is done by the [GraalJS Parser](https://github.com/oracle/graaljs/tree/master/graal-js/src/com.oracle.js.parser).
[Standard visitor pattern](https://github.com/ShiftLeftSecurity/js2cpg/blob/master/src/main/scala/io/shiftleft/js2cpg/parser/GeneralizingAstVisitor.scala) is used to traverse the resulting JS AST afterwards for our CPG-generation.

## CPG-Generation

The CPG-generation phase is responsible for actually generating the CPG using various [passes](https://github.com/ShiftLeftSecurity/js2cpg/tree/master/src/main/scala/io/shiftleft/js2cpg/cpg/passes).
The actual magic happens within the [AstCreator](https://github.com/ShiftLeftSecurity/js2cpg/blob/master/src/main/scala/io/shiftleft/js2cpg/cpg/passes/astcreation/AstCreator.scala).

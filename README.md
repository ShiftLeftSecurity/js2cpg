[![Release](https://github.com/ShiftLeftSecurity/js2cpg/actions/workflows/release.yml/badge.svg)](https://github.com/ShiftLeftSecurity/js2cpg/actions/workflows/release.yml)

# js2cpg Frontend

A Javascript to CPG frontend.

# Installation

You can build `js2cpg` by running the command below.

``` bash
sbt stage
```

After running `js2cpg` by invoking `./js2cpg.sh` you should be able to see the
output below.

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
  --package-json <value>   path to the projects package.json (path relative to <src> or absolute path; defaults to '<src>/package.json')
  --output <value>         CPG output file name (defaults to `cpg.bin.zip`)
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
  --exclude-private-deps   excludes private modules/dependencies in 'node_modules/' (defaults to `false`)
  --private-deps-ns <dep1>,<dep2>,...
                           additional private dependencies to be analyzed from 'node_modules'
  --include-configs        include configuration files (*.conf.js, *.config.js, *.json)
  --include-html           include HTML files (*.html)
```

`js2cpg` requires at least one argument `<srcdir>`. `srcdir` is path
to the project directory from which you would like to generate a CPG (it can be
either a directory containing `.js` files or a single `.js` file).
The option `output` parameter describes the location in the file system where
the CPG should be stored to.

# Example

The example below (`simple.js`), contains two important functions illustrating
a textbook example of a SQL injection: the source `getUserInput()` and the sink
`connection.query()`. The function `getUserInput()` obtains some user input
from the environment which is later used to construct a SQL query.

```javascript
const mysql = require('mysql');

var connection = mysql.createConnection();

connection.connect();

function getUserInput() {
    return "";
}

function lookForProperty(property) {
    var query = property;
    queryDatabase(query);
}

function queryDatabase(sql) {
    var result = "";
    connection.query("SELECT * from table WHERE " + sql, function (err, rows, fields) {
        if (err != "") console.log("error");
        result = rows[0].solution;
    });
    return result;
}

var input = getUserInput();
lookForProperty(input);

connection.end();
```

The command below creates a CPG for the js code contained in `simple.js` and
writes it to `/tmp/cpg.bin.zip`.

```
sbt stage
./js2cpg.sh src/test/resources/simple/ --output /tmp/cpg.bin.zip
```

You can then find the SQLi vulnerability by executing the following commands in
ocular:

```
 ██████╗  ██████╗██╗   ██╗██╗      █████╗ ██████╗
██╔═══██╗██╔════╝██║   ██║██║     ██╔══██╗██╔══██╗
██║   ██║██║     ██║   ██║██║     ███████║██████╔╝
██║   ██║██║     ██║   ██║██║     ██╔══██║██╔══██╗
╚██████╔╝╚██████╗╚██████╔╝███████╗██║  ██║██║  ██║
 ╚═════╝  ╚═════╝ ╚═════╝ ╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝
Version: custom
Commands:
loadCpg("name-of-input-file", ["overlay-name", ...])
Welcome to ShiftLeft Ocular
ocular> loadCpg("/tmp/cpg.bin.zip")
ocular> val source = cpg.method.fullName(".*getUserInput.*").methodReturn
source: io.shiftleft.queryprimitives.steps.types.structure.MethodReturn[shapeless.HNil] = io.shiftleft.queryprimitives.steps.types.structure.MethodReturn@237ee2e1
ocular> val sink = cpg.method.fullName(".*query.*").parameter
sink: io.shiftleft.queryprimitives.steps.types.structure.MethodParameter[shapeless.HNil] = io.shiftleft.queryprimitives.steps.types.structure.MethodParameter@322f84fd
ocular> sink.reachableBy(source).l
2019-06-04 17:47:33.601 [main] INFO mainTasksSize: 3, reachedSource: 1,
res3: List[TrackingPoint] = List((13L, "RET", "BY_VALUE", "ANY", Some(8), None, Some(0), None, Some(2147483647)))
```

# Overview

`js2cpg` operates in the two major steps: Preprocessing and CPG-Generation which are explained below.

## Preliminaries

The execution of `js2cpg` is divided into two phases: Preprocessing and
CPG-generation. The preprocessing phase performs parsing, used identifier extraction and
extracts class declaration information from the code-base. The CPG generation generates
the actual CPG format. Both phases are divided into smaller steps/passes each of
which traverses the original abstract syntax tree (AST) provided by the
js parser. 

In order to map the original AST nodes to the AST proto nodes (across different
passes), we use the class `NodeIdentifier`. This mapping is required because a
pass that is executed at a later point in time may need access to a CPG proto
node that has been produced by an earlier pass. For example, the `CFG
generation` pass adds CFG edges between nodes which were already produced during
the `AST generation` step. Because the `NodeIdentifier` provides the mapping
`(original AST Node, Node Kind) -> Proto Node`, it allows us to retrieve the
corresponding proto node for every original AST node provided by the js parser
at a later point in time. Note that a direct mapping `original AST Node -> Proto
Node` is not possible because some AST nodes (e.g., function Node), require
multiple proto nodes (e.g, `METHOD`, `METHOD_RETURN`).

## Preprocessing

During the preprocessing phase, the whole Javascript code base is processed once
with the following 3 steps/passes:

1. Parsing: In this step, `js2cpg` generates an AST for every `.js` contained in
   the source directory.
2. Extraction of all used identifiers 
   (for the conflict-free generation/naming of artificial variables later on).
3. Extraction of class declaration information.

## CPG-Generation

The CPG-Generation phase is responsible for actually generating the CPG in proto
format with the following steps/passes:

1. BasicAstBuilderPass: This step produces the CPG-AST nodes in proto format. 
2. ControlFlowBuilderPass: This step augments the CPG in proto format with control-flow
   information. This pass is started for each and every method that is encountered during the
   AST traversal during the previous step.
3. Enhancements: Here, meta-data, external types, built-in types, and dependencies in their
   corresponding proto format are created.

package io.shiftleft.js2cpg.cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.{CpgPass, DiffGraph, KeyPool}
import io.shiftleft.codepropertygraph.generated.nodes.NewDependency
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.js2cpg.parser.PackageJsonParser

import java.nio.file.Paths

class DependenciesPass(cpg: Cpg, config: Config, keyPool: KeyPool)
    extends CpgPass(cpg, keyPool = Some(keyPool)) {

  override def run(): Iterator[DiffGraph] = {
    val diffGraph = DiffGraph.newBuilder

    val packagesJsons =
      (FileUtils
        .getFileTree(Paths.get(config.srcDir), config, List(".json"))
        .filter(_.toString.endsWith(PackageJsonParser.PACKAGE_JSON_FILENAME)) :+
        config.createPathForPackageJson()).toSet

    val dependencies: Map[String, String] =
      packagesJsons.flatMap(p => PackageJsonParser.dependencies(p)).toMap

    dependencies.foreach {
      case (name, version) =>
        val dep = NewDependency()
          .name(name)
          .version(version)
        diffGraph.addNode(dep)
    }

    Iterator(diffGraph.build())
  }

}

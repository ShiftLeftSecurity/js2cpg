package io.shiftleft.js2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.NewDependency
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.js2cpg.parser.{FreshJsonParser, PackageJsonParser}
import io.shiftleft.passes.CpgPass

import java.nio.file.Paths

class DependenciesPass(cpg: Cpg, config: Config) extends CpgPass(cpg) {

  private def dependenciesForPackageJsons(): Map[String, String] = {
    val packagesJsons =
      (FileUtils
        .getFileTree(Paths.get(config.srcDir), config, List(".json"))
        .filter(_.toString.endsWith(FileDefaults.PACKAGE_JSON_FILENAME)) :+
        config.createPathForPackageJson()).toSet
    packagesJsons.flatMap(p => PackageJsonParser.dependencies(p)).toMap
  }

  private def dependenciesForFreshJsons(): Map[String, String] = {
    FreshJsonParser.findImportMapPaths(config).flatMap(p => FreshJsonParser.dependencies(p)).toMap
  }

  override def run(diffGraph: DiffGraphBuilder): Unit = {
    val dependencies = dependenciesForPackageJsons() ++ dependenciesForFreshJsons()
    dependencies.foreach { case (name, version) =>
      val dep = NewDependency().name(name).version(version)
      diffGraph.addNode(dep)
    }
  }

}

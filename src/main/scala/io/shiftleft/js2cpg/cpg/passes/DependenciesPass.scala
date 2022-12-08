package io.shiftleft.js2cpg.cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.NewDependency
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileUtils
import io.shiftleft.js2cpg.parser.{FreshJsonParser, PackageJsonParser}
import io.shiftleft.passes.CpgPass

import java.nio.file.Paths

object DependenciesPass {
  def dependenciesForPackageJsons(config: Config): Map[String, String] = {
    val packagesJsons = (FileUtils
      .getFileTree(Paths.get(config.srcDir), config, List(".json"))
      .filter(_.toString.endsWith(PackageJsonParser.PACKAGE_JSON_FILENAME)) :+
      config.createPathForPackageJson()).toSet
    packagesJsons.flatMap(p => PackageJsonParser.dependencies(p)).toMap
  }

  def dependenciesForFreshJsons(config: Config): Map[String, String] = FreshJsonParser
    .findImportMapPaths(config, includeDenoConfig = false)
    .flatMap(p => FreshJsonParser.dependencies(p))
    .toMap
}

class DependenciesPass(cpg: Cpg, config: Config) extends CpgPass(cpg) {

  import DependenciesPass._

  override def run(diffGraph: DiffGraphBuilder): Unit = {
    val dependencies = dependenciesForPackageJsons(config) ++ dependenciesForFreshJsons(config)
    dependencies.foreach { case (name, version) =>
      val dep = NewDependency()
        .name(name)
        .version(version)
      diffGraph.addNode(dep)
    }
  }

}

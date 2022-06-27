package io.shiftleft.js2cpg.cpg.passes

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.Languages
import io.shiftleft.codepropertygraph.generated.nodes.NewMetaData
import io.shiftleft.passes.{KeyPool, SimpleCpgPass}
import org.slf4j.LoggerFactory

class JsMetaDataPass(cpg: Cpg, keyPool: KeyPool, hash: String, root: String)
    extends SimpleCpgPass(cpg, keyPool = Some(keyPool)) {

  private val logger = LoggerFactory.getLogger(getClass)

  override def run(diffGraph: DiffGraphBuilder): Unit = {
    logger.debug("Generating meta-data.")
    val absolutePathToRoot = File(root).path.toAbsolutePath.toString
    val metaNode           = NewMetaData().language(Languages.JAVASCRIPT).hash(hash).root(absolutePathToRoot)
    diffGraph.addNode(metaNode)
  }

}

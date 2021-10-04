package io.shiftleft.js2cpg.cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.Languages
import io.shiftleft.codepropertygraph.generated.nodes.NewMetaData
import io.shiftleft.passes.{CpgPass, DiffGraph, KeyPool}
import org.slf4j.LoggerFactory

class JsMetaDataPass(cpg: Cpg, keyPool: KeyPool, hash: String)
    extends CpgPass(cpg, keyPool = Some(keyPool)) {

  private val logger = LoggerFactory.getLogger(getClass)

  override def run(): Iterator[DiffGraph] = {
    logger.debug(s"Generating meta-data.")

    val diffGraph = DiffGraph.newBuilder
    val metaNode  = NewMetaData().language(Languages.JAVASCRIPT).hash(hash)
    diffGraph.addNode(metaNode)
    Iterator(diffGraph.build())
  }

}

package io.shiftleft.js2cpg.cpg.datastructures.scope

import io.shiftleft.codepropertygraph.generated.nodes.NewNode

case class ResolvedReference(variableNodeId: NewNode, origin: PendingReference)

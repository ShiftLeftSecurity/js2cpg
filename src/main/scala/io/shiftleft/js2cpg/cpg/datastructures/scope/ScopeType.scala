package io.shiftleft.js2cpg.cpg.datastructures.scope

sealed trait ScopeType
object MethodScope extends ScopeType
object BlockScope  extends ScopeType

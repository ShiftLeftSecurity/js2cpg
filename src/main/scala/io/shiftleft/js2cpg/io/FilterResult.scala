package io.shiftleft.js2cpg.io

import java.nio.file.Path

sealed trait FilterResult

case class Accepted()                           extends FilterResult
case class Rejected(path: Path, reason: String) extends FilterResult
case class NotValid()                           extends FilterResult

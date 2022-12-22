package io.shiftleft.js2cpg.utils

sealed abstract class JmxMetric

case class JmxMemoryMetric(usedMem: Long, committedMem: Long) extends JmxMetric
case class JmxCpuMetric(cpu: Object, threadCount: Long)       extends JmxMetric
case class JmxGCMetric(
  parCollectionCount: Long,
  parCollectionTime: Long,
  conCollectionCount: Long,
  conCollectionTime: Long
) extends JmxMetric

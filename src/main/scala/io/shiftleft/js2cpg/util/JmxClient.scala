package io.shiftleft.js2cpg.util

import javax.management.ObjectName
import javax.management.openmbean.CompositeData
import javax.management.remote.{JMXConnector, JMXConnectorFactory, JMXServiceURL}

import scala.annotation.tailrec

abstract class JmxClient {
  protected def init(port: Int, retries: Int = 30): Option[JMXConnector] = {
    val url = new JMXServiceURL(s"service:jmx:rmi:///jndi/rmi://localhost:$port/jmxrmi")

    @tailrec
    def withRetries(url: JMXServiceURL, retriesLeft: Int): Option[JMXConnector] = {
      try {
        val jmxc = Some(JMXConnectorFactory.connect(url, null))
        jmxc.foreach(_.connect())
        jmxc
      } catch {
        case _: Throwable =>
          if (retriesLeft > 0) {
            Thread.sleep(100)
            withRetries(url, retriesLeft - 1)
          } else {
            None
          }
      }
    }
    withRetries(url, retries)
  }

  /** Returns an optional JMXMetric with raw JVM memory stats.
    */
  def memoryMetric(jmxc: Option[JMXConnector]): Option[JmxMemoryMetric] = {
    try {
      jmxc flatMap { jmxc =>
        val memoryMbean =
          jmxc.getMBeanServerConnection.getAttribute(
            new ObjectName("java.lang:type=Memory"),
            "HeapMemoryUsage"
          )
        val cd = memoryMbean.asInstanceOf[CompositeData]

        Some(
          JmxMemoryMetric(cd.get("used").asInstanceOf[Long], cd.get("committed").asInstanceOf[Long])
        )
      }
    } catch {
      case _: Throwable =>
        // Ok to ignore, the application finished.
        None
    }
  }

  /** Returns an optional JMXMetric with raw JVM cpu stats.
    */
  def cpuMetric(jmxc: Option[JMXConnector]): Option[JmxCpuMetric] = {
    try {
      jmxc flatMap { jmxc =>
        val connection = jmxc.getMBeanServerConnection
        val osMbean =
          connection
            .getAttribute(new ObjectName("java.lang:type=OperatingSystem"), "ProcessCpuTime")
        val threads =
          connection
            .getAttribute(new ObjectName("java.lang:type=Threading"), "ThreadCount")
            .asInstanceOf[Long]
        Some(JmxCpuMetric(osMbean, threads))
      }
    } catch {
      case _: Throwable =>
        // Ok to ignore, the application finished.
        None
    }
  }

  /** Returns an optional JMXMetric with raw JVM GC stats.
    */
  def gcMetric(jmxc: Option[JMXConnector]): Option[JmxGCMetric] = {
    try {
      jmxc flatMap { jmxc =>
        val connection = jmxc.getMBeanServerConnection
        val gcParCollectionCount =
          connection
            .getAttribute(
              new ObjectName("java.lang:type=GarbageCollector,name=ParNew"),
              "CollectionCount"
            )
            .asInstanceOf[Long]
        val gcParCollectionTime =
          connection
            .getAttribute(
              new ObjectName("java.lang:type=GarbageCollector,name=ParNew"),
              "CollectionTime"
            )
            .asInstanceOf[Long]
        val gcConCollectionCount =
          connection
            .getAttribute(
              new ObjectName("java.lang:type=GarbageCollector,name=ConcurrentMarkSweep"),
              "CollectionCount"
            )
            .asInstanceOf[Long]
        val gcConCollectionTime =
          connection
            .getAttribute(
              new ObjectName("java.lang:type=GarbageCollector,name=ConcurrentMarkSweep"),
              "CollectionTime"
            )
            .asInstanceOf[Long]

        Some(
          JmxGCMetric(
            gcParCollectionCount,
            gcParCollectionTime,
            gcConCollectionCount,
            gcConCollectionTime
          )
        )
      }
    } catch {
      case _: Throwable =>
        // Ok to ignore, the application finished.
        None
    }
  }

}

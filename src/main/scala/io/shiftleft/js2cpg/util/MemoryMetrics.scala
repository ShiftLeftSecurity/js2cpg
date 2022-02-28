package io.shiftleft.js2cpg.util

import io.shiftleft.js2cpg.core.Config
import org.slf4j.LoggerFactory

import scala.util.Using

object MemoryMetrics {

  private class JmxRunnable(var port: Int, var sleep: Int) extends JmxClient with Runnable with AutoCloseable {

    private val logger  = LoggerFactory.getLogger(classOf[JmxRunnable])
    private val MB      = 1024 * 1024
    private var running = false

    override def run(): Unit = {
      val optJmx = init(port, 20)
      if (optJmx.isDefined) {
        running = true
        logger.info("Enabling JVM metrics logging")
        while (running) {
          memoryMetric(optJmx).foreach { value =>
            logger.debug(
              "Memory used/committed (MB): " +
                (value.usedMem / MB) + " / " + (value.committedMem / MB)
            )
          }

          gcMetric(optJmx).foreach { value =>
            logger.debug(
              "GC: Parallel count - " + value.parCollectionCount +
                ", Parallel time - " + value.parCollectionTime +
                "Concurrent count - " + value.conCollectionCount +
                ", Concurrent time - " + value.conCollectionCount
            )
          }

          cpuMetric(optJmx).foreach { value =>
            logger.debug("CPU: Thread count - " + value.threadCount)
          }

          try { Thread.sleep(sleep.toLong) }
          catch {
            case _: InterruptedException =>
              // ignore, get out of the loop
              running = false
          }
        }
      }
    }

    override def close(): Unit = {
      running = false
    }
  }

  def withMemoryMetrics(config: Config)(work: => Unit): Unit = config.jvmMetrics match {
    case Some(port) =>
      Using(new JmxRunnable(port, 5000)) { monitor =>
        val t = new Thread(monitor)
        t.setName("js2cpg-jvm-monitor")
        t.start()
        work
      }
    case None => work
  }

}

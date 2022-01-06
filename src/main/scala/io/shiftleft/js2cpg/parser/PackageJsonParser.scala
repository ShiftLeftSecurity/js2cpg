package io.shiftleft.js2cpg.parser

import java.nio.file.{Path, Paths}
import io.shiftleft.js2cpg.io.FileUtils
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode

import scala.collection.concurrent.TrieMap
import scala.util.Try
import scala.util.Using
import scala.jdk.CollectionConverters._

object PackageJsonParser {
  private val logger = LoggerFactory.getLogger(PackageJsonParser.getClass)

  val PACKAGE_JSON_FILENAME      = "package.json"
  val PACKAGE_JSON_LOCK_FILENAME = "package-lock.json"

  private val projectDependencies = Seq(
    "dependencies",
    "devDependencies",
    "peerDependencies",
    "optionalDependencies"
  )

  private val cachedDependencies: TrieMap[Path, Map[String, String]] = TrieMap.empty

  def dependencies(packageJsonPath: Path): Map[String, String] =
    cachedDependencies.getOrElseUpdate(
      packageJsonPath, {
        val depsPath     = packageJsonPath
        val lockDepsPath = packageJsonPath.resolveSibling(Paths.get(PACKAGE_JSON_LOCK_FILENAME))

        val lockDeps = Try {
          val content      = FileUtils.readLinesInFile(lockDepsPath).mkString("\n")
          val objectMapper = new ObjectMapper
          val packageJson  = objectMapper.readTree(content)

          var depToVersion = Map.empty[String, String]
          val dependencyIt = Option(packageJson.get("dependencies"))
            .map(_.fields().asScala)
            .getOrElse(Iterator.empty)
          dependencyIt.foreach {
            case entry: java.util.Map.Entry[String, JsonNode] =>
              val depName     = entry.getKey
              val versionNode = entry.getValue.get("version")
              if (versionNode != null) {
                depToVersion = depToVersion.updated(depName, versionNode.asText())
              }
          }
          depToVersion
        }.toOption

        // lazy val because we only evaluate this in case no package lock file is available.
        lazy val deps = Try {
          val content      = FileUtils.readLinesInFile(depsPath).mkString("\n")
          val objectMapper = new ObjectMapper
          val packageJson  = objectMapper.readTree(content)

          var depToVersion = Map.empty[String, String]
          projectDependencies
            .foreach { dependency =>
              val dependencyIt = Option(packageJson.get(dependency))
                .map(_.fields().asScala)
                .getOrElse(Iterator.empty)
              dependencyIt.foreach {
                case entry: java.util.Map.Entry[String, JsonNode] =>
                  depToVersion = depToVersion.updated(entry.getKey, entry.getValue.asText())
              }
            }
          depToVersion
        }.toOption

        if (lockDeps.isDefined && lockDeps.get.nonEmpty) {
          logger.debug(s"Loaded dependencies from '$lockDepsPath'.")
          lockDeps.get
        } else {
          if (deps.isDefined && deps.get.nonEmpty) {
            logger.debug(s"Loaded dependencies from '$depsPath'.")
            deps.get
          } else {
            logger.debug(
              s"No project dependencies found in $PACKAGE_JSON_FILENAME or $PACKAGE_JSON_LOCK_FILENAME at '${depsPath.getParent}'.")
            Map.empty
          }
        }
      }
    )

}

package io.shiftleft.js2cpg.parser

import com.fasterxml.jackson.core.JsonParser

import java.nio.file.{Path, Paths}
import io.shiftleft.js2cpg.io.FileUtils
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.lang.StringUtils

import scala.collection.concurrent.TrieMap
import scala.util.Try
import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success

object PackageJsonParser {
  private val logger = LoggerFactory.getLogger(PackageJsonParser.getClass)

  val PACKAGE_JSON_FILENAME: String   = "package.json"
  val JSON_LOCK_FILENAME: String      = "package-lock.json"
  val NPM_SHRINKWRAP_FILENAME: String = "npm-shrinkwrap.json"
  val ANGULAR_JSON_FILENAME: String   = "angular.json"
  val PNPM_WS_FILENAME: String        = "pnpm-workspace.yaml"
  val PNPM_LOCK_FILENAME: String      = "pnpm-lock.yaml"
  val PNPM_LOCK_FILENAME_BAK: String  = "pnpm-lock.yaml.bak"
  val YARN_LOCK_FILENAME: String      = "yarn.lock"
  val YARN_LOCK_FILENAME_BAK: String  = "yarn.lock.bak"
  val WEBPACK_CONFIG_FILENAME: String = "webpack.config.js"

  val PROJECT_CONFIG_FILES: List[String] = List(
    JSON_LOCK_FILENAME,
    YARN_LOCK_FILENAME,
    PNPM_LOCK_FILENAME,
    // pnpm workspace config file is not required as we manually descent into sub-project:
    PNPM_WS_FILENAME,
    NPM_SHRINKWRAP_FILENAME,
    WEBPACK_CONFIG_FILENAME,
    ANGULAR_JSON_FILENAME
  )

  val PROJECT_DEPENDENCIES: Seq[String] = Seq(
    "dependencies",
    "devDependencies",
    "peerDependencies",
    "peerDependenciesMeta",
    "optionalDependencies",
    "resolutions",
    "bundleDependencies",
    "bundledDependencies"
  )

  private val cachedDependencies: TrieMap[Path, Map[String, String]] = TrieMap.empty

  def removeComments(json: String): String = {
    val mapper = new ObjectMapper
    mapper.enable(JsonParser.Feature.ALLOW_COMMENTS)
    mapper.writeValueAsString(mapper.readTree(json))
  }

  def isValidProjectPackageJson(packageJsonPath: Path): Boolean = {
    if (packageJsonPath.toString.endsWith(PackageJsonParser.PACKAGE_JSON_FILENAME)) {
      val isNotEmpty = Try(FileUtils.readLinesInFile(packageJsonPath)) match {
        case Success(content) =>
          content.forall(l => StringUtils.isNotBlank(StringUtils.normalizeSpace(l)))
        case Failure(_) => false
      }
      isNotEmpty && dependencies(packageJsonPath).nonEmpty
    } else {
      false
    }
  }

  def dependencies(packageJsonPath: Path): Map[String, String] =
    cachedDependencies.getOrElseUpdate(
      packageJsonPath, {
        val depsPath     = packageJsonPath
        val lockDepsPath = packageJsonPath.resolveSibling(Paths.get(JSON_LOCK_FILENAME))

        val lockDeps = Try {
          val content      = FileUtils.readLinesInFile(lockDepsPath).mkString("\n")
          val objectMapper = new ObjectMapper
          val packageJson  = objectMapper.readTree(content)

          var depToVersion = Map.empty[String, String]
          val dependencyIt = Option(packageJson.get("dependencies"))
            .map(_.fields().asScala)
            .getOrElse(Iterator.empty)
          dependencyIt.foreach { entry =>
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
          PROJECT_DEPENDENCIES
            .foreach { dependency =>
              val dependencyIt = Option(packageJson.get(dependency))
                .map(_.fields().asScala)
                .getOrElse(Iterator.empty)
              dependencyIt.foreach { entry =>
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
              s"No project dependencies found in $PACKAGE_JSON_FILENAME or $JSON_LOCK_FILENAME at '${depsPath.getParent}'."
            )
            Map.empty
          }
        }
      }
    )

}

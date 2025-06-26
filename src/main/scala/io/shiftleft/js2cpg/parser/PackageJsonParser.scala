package io.shiftleft.js2cpg.parser

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import io.shiftleft.js2cpg.io.FileDefaults
import io.shiftleft.utils.IOUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters.SetHasAsScala
import scala.util.{Failure, Success, Try}

object PackageJsonParser {
  private val logger = LoggerFactory.getLogger(PackageJsonParser.getClass)

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
    if (packageJsonPath.toString.endsWith(FileDefaults.PACKAGE_JSON_FILENAME)) {
      val isNotEmpty = Try(IOUtils.readLinesInFile(packageJsonPath)) match {
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
        val lockDepsPath = packageJsonPath.resolveSibling(Paths.get(FileDefaults.JSON_LOCK_FILENAME))

        val lockDeps = Try {
          val content      = IOUtils.readLinesInFile(lockDepsPath).mkString
          val packageJson  = new ObjectMapper().readTree(content)
          val dependencyIt = Option(packageJson.get("dependencies")).map(_.properties().asScala).getOrElse(Set.empty)
          dependencyIt.flatMap { entry =>
            val depName     = entry.getKey
            val versionNode = entry.getValue.get("version")
            if (versionNode != null) Some(depName -> versionNode.asText()) else None
          }.toMap
        }.toOption

        // lazy val because we only evaluate this in case no package lock file is available.
        lazy val deps = Try {
          val content     = IOUtils.readLinesInFile(depsPath).mkString
          val packageJson = new ObjectMapper().readTree(content)
          PROJECT_DEPENDENCIES.flatMap { dependency =>
            val dependencyIt = Option(packageJson.get(dependency)).map(_.properties().asScala).getOrElse(Set.empty)
            dependencyIt.map { entry => entry.getKey -> entry.getValue.asText() }
          }.toMap
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
              s"No project dependencies found in ${FileDefaults.PACKAGE_JSON_FILENAME} or ${FileDefaults.JSON_LOCK_FILENAME} at '${depsPath.getParent}'."
            )
            Map.empty
          }
        }
      }
    )

}

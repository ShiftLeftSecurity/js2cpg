package io.shiftleft.js2cpg.parser

import java.nio.file.{Path, Paths}
import io.shiftleft.js2cpg.io.FileUtils
import org.slf4j.LoggerFactory
import play.api.libs.json.Json

import scala.collection.concurrent.TrieMap
import scala.util.Try
import scala.util.Using

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
          val content = FileUtils.readLinesInFile(lockDepsPath).mkString("\n")
          (Json.parse(content) \ "dependencies")
            .asOpt[Map[String, Map[String, String]]]
            .map { versions =>
              versions.map {
                case (depName, entry) => depName -> entry("version")
              }
            }
            .getOrElse(Map.empty)
        }.toOption

        // lazy val because we only evaluate this in case no package lock file is available.
        lazy val deps = Try {
          val content     = FileUtils.readLinesInFile(depsPath).mkString("\n")
          val packageJson = Json.parse(content)
          projectDependencies
            .flatMap { dependency =>
              (packageJson \ dependency).asOpt[Map[String, String]]
            }
            .flatten
            .toMap
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

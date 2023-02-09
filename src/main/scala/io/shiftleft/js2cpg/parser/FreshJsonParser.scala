package io.shiftleft.js2cpg.parser

import java.nio.file.{Files, Path, Paths}
import io.shiftleft.js2cpg.io.FileUtils
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.databind.ObjectMapper
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.preprocessing.TypescriptTranspiler
import io.shiftleft.utils.IOUtils

import scala.collection.concurrent.TrieMap
import scala.util.Try
import scala.jdk.CollectionConverters._

object FreshJsonParser {
  private val logger = LoggerFactory.getLogger(FreshJsonParser.getClass)

  private val cachedDependencies: TrieMap[Path, Map[String, String]] = TrieMap.empty

  private def dropLastSlash(str: String): String = str.takeRight(1) match {
    case "/" => str.dropRight(1)
    case _   => str
  }

  private def cleanKey(key: String): String =
    dropLastSlash(key).replaceFirst("\\$", "")

  private def extractVersion(str: String): String = {
    val dropped = dropLastSlash(str.replace("mod.ts", ""))
    dropped.substring(dropped.lastIndexOf("@") + 1, dropped.length)
  }

  def findImportMapPaths(config: Config, includeDenoConfig: Boolean): Set[Path] = {
    val objectMapper = new ObjectMapper
    FileUtils
      .getFileTree(Paths.get(config.srcDir), config, List(".json"))
      .filter(_.endsWith(TypescriptTranspiler.DENO_CONFIG))
      .flatMap { file =>
        val packageJson = objectMapper.readTree(Files.readAllBytes(file))
        val importMap   = Option(packageJson.path("importMap").asText()).map(file.resolveSibling)

        if (includeDenoConfig) Iterable(file) ++ importMap
        else importMap
      }
      .filter(Files.exists(_))
      .toSet
  }

  def dependencies(freshJsonPath: Path): Map[String, String] =
    cachedDependencies.getOrElseUpdate(
      freshJsonPath, {
        val deps = Try {
          val content      = IOUtils.readLinesInFile(freshJsonPath).mkString("\n")
          val objectMapper = new ObjectMapper
          val json         = objectMapper.readTree(content)

          var depToVersion = Map.empty[String, String]
          val dependencyIt = Option(json.get("imports"))
            .map(_.fields().asScala)
            .getOrElse(Iterator.empty)
          dependencyIt.foreach {
            case entry if entry.getKey.startsWith("@") => // ignored
            case entry =>
              depToVersion = depToVersion.updated(cleanKey(entry.getKey), extractVersion(entry.getValue.asText()))
          }
          depToVersion
        }.toOption

        if (deps.isDefined) {
          logger.debug(s"Loaded dependencies from '$freshJsonPath'.")
          deps.get
        } else {
          logger.debug(
            s"No project dependencies found in ${freshJsonPath.getFileName} at '${freshJsonPath.getParent}'."
          )
          Map.empty
        }
      }
    )

}

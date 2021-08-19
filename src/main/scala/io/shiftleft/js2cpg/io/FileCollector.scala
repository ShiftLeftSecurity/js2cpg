package io.shiftleft.js2cpg.io

import org.slf4j.LoggerFactory

import java.io.IOException
import java.nio.file.{FileSystemLoopException, FileVisitResult, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object FileCollector {
  private val logger = LoggerFactory.getLogger(FileCollector.getClass)

  def apply(pathFilter: PathFilter): FileCollector = new FileCollector(pathFilter)
}

class FileCollector private (pathFilter: PathFilter) extends SimpleFileVisitor[Path] {

  import FileCollector.logger

  private val result: ArrayBuffer[Path] = mutable.ArrayBuffer.empty[Path]

  private val excluded: mutable.Map[Path, String] = mutable.HashMap.empty[Path, String]

  def files: List[Path] = List.from(result)

  def excludedPaths: Map[Path, String] = Map.from(excluded)

  def wasExcluded(path: Path): Boolean =
    excluded.contains(pathFilter.rootPath.relativize(path))

  override def preVisitDirectory(path: Path, attrs: BasicFileAttributes): FileVisitResult =
    pathFilter(path) match {
      case Accepted() =>
        FileVisitResult.CONTINUE
      case Rejected(path, reason) =>
        excluded(path) = reason
        FileVisitResult.SKIP_SUBTREE
      case NotValid() =>
        FileVisitResult.SKIP_SUBTREE
    }

  override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
    pathFilter(path) match {
      case Accepted()             => result.addOne(path)
      case Rejected(path, reason) => excluded(path) = reason
      case NotValid()             =>
    }
    FileVisitResult.CONTINUE
  }

  override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = {
    exc match {
      case loop: FileSystemLoopException =>
        logger.debug(s"Cyclic symbolic link detected for file '$file'", loop)
      case _ =>
        logger.debug(s"Unable to visit file '$file'", exc)
    }
    FileVisitResult.CONTINUE
  }
}

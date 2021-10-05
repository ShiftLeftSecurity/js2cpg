package io.shiftleft.js2cpg.io

import better.files.File

import java.io.Reader
import java.math.BigInteger
import java.nio.charset.{CharsetDecoder, CodingErrorAction}
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import io.shiftleft.js2cpg.core.Config
import io.shiftleft.js2cpg.io.FileDefaults._
import org.slf4j.LoggerFactory

import java.nio.file.attribute.BasicFileAttributes
import java.security.{DigestInputStream, MessageDigest}
import scala.collection.concurrent.TrieMap
import scala.collection.{SortedMap, mutable}
import scala.io.{BufferedSource, Codec, Source}
import scala.jdk.CollectionConverters._

object FileUtils {

  private val logger = LoggerFactory.getLogger(FileUtils.getClass)

  // we only want to print excluded files and folders once (Path -> Reason as String)
  private val excludedPaths = TrieMap.empty[Path, String]

  def md5(files: Seq[Path]): String = {
    val md = MessageDigest.getInstance("MD5")
    files.sortBy(_.toRealPath().toString).foreach { path =>
      val dis = new DigestInputStream(Files.newInputStream(path), md)
      while (dis.available() > 0) {
        dis.read()
      }
      dis.close()
    }
    md.digest().map(b => String.format("%02x", Byte.box(b))).mkString
  }

  def logAndClearExcludedPaths(): Unit = {
    excludedPaths.foreach {
      case (path, reason) =>
        logger.debug(s"Excluded '$path' ($reason).")
    }
    excludedPaths.clear()
  }

  /**
    * Cleans the given path as String and removes unwanted elements that
    * occur during transpilation on the Windows platform and/or CI environments.
    */
  def cleanPath(sourceFileName: String): String = {
    val replaceDots = sourceFileName.replace("../", "")
    if ("""file:///.:.*""".r.matches(replaceDots)) {
      replaceDots.replace("file:///", "")
    } else {
      replaceDots
    }
  }

  /**
    * Creates a new UTF-8 decoder.
    * Sadly, instances of CharsetDecoder are not thread-safe as the doc states:
    * 'Instances of this class are not safe for use by multiple concurrent threads.'
    * (copied from: [[java.nio.charset.CharsetDecoder]])
    *
    * As we are using it in a [[io.shiftleft.passes.ParallelCpgPass]] it needs to be thread-safe.
    * Hence, we make sure to create a new instance everytime.
    */
  private def createDecoder(): CharsetDecoder =
    Codec.UTF8.decoder
      .onMalformedInput(CodingErrorAction.REPLACE)
      .onUnmappableCharacter(CodingErrorAction.REPLACE)

  private val validUnicodeRegex = """([a-zA-Z0-9]){4}""".r

  private val boms = Set(
    '\uefbb', // UTF-8
    '\ufeff', // UTF-16 (BE)
    '\ufffe' // UTF-16 (LE)
  )

  def getFileTree(rootPath: Path,
                  config: Config,
                  extension: String,
                  filterIgnoredFiles: Boolean = true): List[Path] = {
    val fileCollector = FileCollector(
      PathFilter(rootPath, config, filterIgnoredFiles, Some(extension)))
    Files.walkFileTree(rootPath, fileCollector)
    excludedPaths.addAll(fileCollector.excludedPaths)
    fileCollector.files
  }

  private def copyTo(
      from: File,
      destination: File,
      config: Config,
  )(implicit
    copyOptions: File.CopyOptions = File.CopyOptions(false)): File = {
    val fileCollector = FileCollector(
      PathFilter(from.path, config, filterIgnoredFiles = false, None))
    if (from.isDirectory) {
      Files.walkFileTree(
        from.path,
        new SimpleFileVisitor[Path] {
          private def newPath(subPath: Path): Path =
            destination.path.resolve(from.path.relativize(subPath))

          override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
            fileCollector.preVisitDirectory(dir, attrs) match {
              case c @ FileVisitResult.CONTINUE =>
                Files.createDirectories(newPath(dir))
                c
              case other => other
            }

          override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
            val result = fileCollector.visitFile(file, attrs)
            if (!fileCollector.wasExcluded(file)) {
              Files.copy(file, newPath(file), copyOptions: _*)
            }
            result
          }
        }
      )
    } else {
      Files.copy(from.path, destination.path, copyOptions: _*)
    }
    excludedPaths.addAll(fileCollector.excludedPaths)
    destination
  }

  def copyToDirectory(
      from: File,
      directory: File,
      config: Config
  )(implicit
    copyOptions: File.CopyOptions = File.CopyOptions.default): File = {
    copyTo(from, directory / from.name, config)(copyOptions)
  }

  def bufferedSourceFromFile(path: Path): BufferedSource = {
    Source.fromFile(path.toFile)(createDecoder())
  }

  private def skipBOMIfPresent(reader: Reader): Unit = {
    reader.mark(1)
    val possibleBOM = new Array[Char](1)
    reader.read(possibleBOM)
    if (!boms.contains(possibleBOM(0))) {
      reader.reset()
    }
  }

  private def removeUnpairedSurrogates(input: String): String = {
    var result = input
    """(\\u)""".r.findAllMatchIn(input).foreach { pos =>
      val matchedString = input.substring(pos.start + 2, pos.start + 6)
      if (validUnicodeRegex.matches(matchedString)) {
        val c = new BigInteger(matchedString, 16).intValue().asInstanceOf[Char]
        if (Character.isLowSurrogate(c) || Character.isHighSurrogate(c)) {
          // removing them including leading '\' (needs escapes for backslash itself + for the regex construction)
          result = result.replaceAll("(\\\\)*\\\\u" + matchedString, "")
        }
      }
    }
    result
  }

  def contentFromBufferedSource(bufferedSource: BufferedSource): String = {
    val reader = bufferedSource.bufferedReader()
    skipBOMIfPresent(reader)
    EmScriptenCleaner
      .clean(reader.lines().iterator().asScala)
      .map(removeUnpairedSurrogates)
      .mkString("\n")
  }

  def contentMapFromBufferedSource(bufferedSource: BufferedSource): Map[Int, String] = {
    val reader = bufferedSource.bufferedReader()
    skipBOMIfPresent(reader)
    EmScriptenCleaner
      .clean(reader.lines().iterator().asScala)
      .zipWithIndex
      .map {
        case (line, lineNumber) => lineNumber -> removeUnpairedSurrogates(line)
      }
      .toMap
  }

  def positionLookupTables(source: String): (SortedMap[Int, Int], SortedMap[Int, Int]) = {
    val positionToLineNumber, positionToFirstPositionInLine = mutable.TreeMap.empty[Int, Int]

    val data                = source.toCharArray
    var lineNumber          = 1
    var firstPositionInLine = 0
    var position            = 0

    while (position < data.length) {
      val isNewLine = data(position) == '\n'
      if (isNewLine) {
        positionToLineNumber.put(position, lineNumber)
        lineNumber += 1
        positionToFirstPositionInLine.put(position, firstPositionInLine)
        firstPositionInLine = position + 1
      }
      position += 1
    }

    positionToLineNumber.put(position, lineNumber)
    positionToFirstPositionInLine.put(position, firstPositionInLine)

    (positionToLineNumber, positionToFirstPositionInLine)
  }

  final case class FileStatistics(linesOfCode: Long,
                                  longestLineLength: Int,
                                  containsMarker: Boolean)

  /**
    * Calculates various statistics of the source.
    *  - lines of code
    *  - longest line of code
    *  - containment of a given marker
    *
    * This implementation is just as fast as the unix word count program `wc -l`.
    * By using Scala BufferedSource we gain a lot of performance as it uses
    * a Java PushbackReader and BufferedReader.
    */
  def fileStatistics(source: Source): FileStatistics = {
    var linesOfCode       = 0L
    var longestLineLength = 0
    var containsMarker    = false

    for (line <- source.getLines()) {
      val currLength = line.length
      if (currLength > longestLineLength) {
        longestLineLength = currLength
      }
      if (!containsMarker && EMSCRIPTEN_START_FUNCS.matches(line)) {
        containsMarker = true
      }
      linesOfCode += 1
    }
    FileStatistics(linesOfCode, longestLineLength, containsMarker)
  }

}

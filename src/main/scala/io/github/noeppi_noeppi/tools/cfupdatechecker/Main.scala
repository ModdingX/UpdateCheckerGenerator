package io.github.noeppi_noeppi.tools.cfupdatechecker

import io.github.noeppi_noeppi.tools.cfupdatechecker.cache.FileCache
import io.github.noeppi_noeppi.tools.cursewrapper.api.CurseWrapper
import joptsimple.util.{PathConverter, PathProperties}
import joptsimple.{OptionException, OptionParser}

import java.net.URI
import java.nio.file.{Files, StandardOpenOption}
import scala.jdk.CollectionConverters._

object Main extends App {
  
  val options = new OptionParser(false)
  val specCfg = options.acceptsAll(List("c", "config").asJava, "A list of project ids to generate update checkers for.").withRequiredArg().withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING))
  val specDir = options.acceptsAll(List("d", "dir", "directory").asJava, "Output directory.").withRequiredArg().withValuesConvertedBy(new PathConverter())
  val specCache = options.acceptsAll(List("f", "cache").asJava, "Cache file to use.").withRequiredArg().withValuesConvertedBy(new PathConverter())
  val set = try {
//    options.parse(args: _*)
    options.parse("-c", "/home/tux/dev/util/CfUpdateChecker/test.txt", "-d", "/home/tux/dev/util/CfUpdateChecker/test", "-f", "/home/tux/dev/util/CfUpdateChecker/cache.json")
  } catch {
    case e: OptionException => System.err.println("Option exception: " + e.getMessage); options.printHelpOn(System.err); Util.exit(0)
  }
  if (!set.has(specCfg) || !set.has(specDir)) {
    options.printHelpOn(System.err)
    Util.exit(0)
  }
  
  val projectIdStrings = Files.readAllLines(set.valueOf(specCfg)).asScala.toList.map(str => if (str.contains("#")) str.substring(0, str.indexOf('#')) else str).map(_.strip()).filter(_.nonEmpty)
  projectIdStrings.filter(_.toIntOption.isEmpty).find(str => throw new IllegalStateException("Invalid integer for project id: " + str))

  val basePath = set.valueOf(specDir)
  if (!Files.exists(basePath)) Files.createDirectories(basePath)
  
  val cache = new FileCache
  if (set.has(specCache)) cache.read(set.valueOf(specCache))

  val api = new CurseWrapper(URI.create("https://curse.melanx.de/"))
  for (idString <- projectIdStrings; projectId = idString.toInt) {
    val (slug, json) = UpdateCheckerGenerator.generateUpdateChecker(api, projectId, cache)
    val path = basePath.resolve(slug + ".json")
    val writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    writer.write(Util.GSON.toJson(json) + "\n")
    writer.close()
  }
  
  if (set.has(specCache)) cache.write(set.valueOf(specCache))
}

package io.github.noeppi_noeppi.tools.cfupdatechecker.cache

import com.google.gson.{JsonArray, JsonElement, JsonObject}
import io.github.noeppi_noeppi.tools.cfupdatechecker.Util

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object FileCache {
  val VERSION: Int = 3
}

class FileCache {

  private val changelogs = mutable.Map[(Int, Int), String]()
  private val versions = mutable.Map[(Int, Int), String]()
  
  def changelog(projectId: Int, fileId: Int, changelog: => String): String = changelogs.getOrElseUpdate((projectId, fileId), changelog)
  def version(projectId: Int, fileId: Int, version: => String): String = versions.getOrElseUpdate((projectId, fileId), version)
  
  def read(path: Path): Unit = {
    
    def readFile(data: JsonObject): ((Int, Int), String) = {
      val projectId = data.get("project").getAsInt
      val fileId = data.get("file").getAsInt
      val changelog = data.get("value").getAsString
      ((projectId, fileId), changelog)
    }

    def readMap(data: JsonElement): Map[(Int, Int), String] = {
      data.getAsJsonArray.asScala.toList
        .map((e: JsonElement) => e.getAsJsonObject)
        .map(readFile)
        .toMap
    }

    changelogs.clear()
    versions.clear()
    try {
      if (Files.exists(path)) {
        val reader = Files.newBufferedReader(path)
        val json = Util.INTERNAL.fromJson(reader, classOf[JsonElement])
        reader.close()
        val cacheVersion = json.getAsJsonObject.get("version").getAsInt
        if (cacheVersion == FileCache.VERSION) {
          changelogs.addAll(readMap(json.getAsJsonObject.get("changelogs")))
          versions.addAll(readMap(json.getAsJsonObject.get("versions")))
        }
      }
    } catch {
      case e: Exception =>
        println("Failed to read file cache: " + e.getClass.getSimpleName + ": " + e.getMessage)
        changelogs.clear()
        versions.clear()
    }
  }
  
  def write(path: Path): Unit = {
    def array(elements: Seq[JsonElement]): JsonArray = {
      val array = new JsonArray()
      elements.foreach(array.add)
      array
    }
    
    def writeFile(entry: ((Int, Int), String)): JsonObject = {
      val data = new JsonObject
      data.addProperty("project", entry._1._1)
      data.addProperty("file", entry._1._2)
      data.addProperty("value", entry._2)
      data
    }

    def writeMap(map: Map[(Int, Int), String]): JsonElement = {
      array(map.toSeq.map(writeFile))
    }
    
    try {
      if (!Files.exists(path.getParent)) Files.createDirectories(path.getParent)
      val writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      val json = new JsonObject
      json.addProperty("version", FileCache.VERSION)
      json.add("changelogs", writeMap(changelogs.toMap))
      json.add("versions", writeMap(versions.toMap))
      writer.write(Util.INTERNAL.toJson(json) + "\n")
      writer.close()
    } catch {
      case e: Exception =>
        println("Failed to write file cache: " + e.getClass.getSimpleName + ": " + e.getMessage)
        new JsonArray
    }
  }
}

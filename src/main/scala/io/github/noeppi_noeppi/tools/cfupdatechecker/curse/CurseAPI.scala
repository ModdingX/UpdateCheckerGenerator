package io.github.noeppi_noeppi.tools.cfupdatechecker.curse

import com.google.gson.internal.bind.util.ISO8601Utils
import com.google.gson.{JsonElement, JsonObject}
import io.github.noeppi_noeppi.tools.cfupdatechecker.Util
import io.github.noeppi_noeppi.tools.cfupdatechecker.cache.FileCache
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup

import java.io.{BufferedReader, InputStreamReader, Reader}
import java.net.{HttpURLConnection, URL}
import java.text.ParsePosition
import java.time.{LocalDateTime, ZoneId}
import scala.jdk.CollectionConverters.IterableHasAsScala

object CurseAPI {
  
  def slug(projectId: Int): String = {
    val json = query("addon/" + projectId)
    json.getAsJsonObject.get("slug").getAsString
  }

  def files(projectId: Int, cache: FileCache): List[CurseFile] = {
    val json = query("addon/" + projectId + "/files")
    json.getAsJsonArray.asScala.toList.flatMap(e => file(projectId, e.getAsJsonObject, cache))
  }
  
  private def file(projectId: Int, json: JsonObject, cache: FileCache): Option[CurseFile] = {
    val id = json.get("id").getAsInt
    val name = json.get("fileName").getAsString
    val versions = json.get("gameVersion").getAsJsonArray.asScala.toSet.map((e: JsonElement) => e.getAsString)
    if (!versions.contains("Forge") && (versions.contains("Fabric") || versions.contains("Rift"))) return None
    val date = LocalDateTime.ofInstant(ISO8601Utils.parse(json.get("fileDate").getAsString, new ParsePosition(0)).toInstant, ZoneId.of("UTC"))
    val releaseType = json.get("releaseType").getAsInt
    val changelog = cache.changelog(projectId, id, {
      val changelogHtml = queryPlain("addon/395617/file/" + id + "/changelog")
      normalizeText(Jsoup.parse(changelogHtml).wholeText())
    })
    Some(CurseFile(projectId, id, name, releaseType, versions.removedAll(Set("Forge", "Fabric", "Rift")), date, changelog))
  }
  
  private def normalizeText(str: String): String = {
    val theStr = if (str.startsWith("\"") && str.endsWith("\"")) str.substring(1, str.length - 1) else str
    StringEscapeUtils.unescapeJava(theStr).replace("\r", "").replaceAll("\\n+", "\n").strip()
  }

  private def query(endpoint: String): JsonElement = query(endpoint, reader => Util.GSON.fromJson(reader, classOf[JsonElement]))
  
  private def queryPlain(endpoint: String): String = query(endpoint, reader => {
    val buffered = new BufferedReader(reader)
    buffered.lines().toArray.toList.map(_.toString).mkString("\n")
  })

  private def query[T](endpoint: String, func: Reader => T): T = {
    val url = new URL("https://addons-ecs.forgesvc.net/api/v2/" + endpoint)
    val c = url.openConnection()
    c.addRequestProperty("Accept", "application/json")
    c.addRequestProperty("Cache-Control", "max-age=0")
    c.addRequestProperty("Connection", "keep-alive")
    c.connect()
    val reader = new InputStreamReader(c.getInputStream)
    val t = func(reader)
    reader.close()
    c.getInputStream.close()
    c match {
      case connection: HttpURLConnection => connection.disconnect()
      case _ =>
    }
    t
  }
}

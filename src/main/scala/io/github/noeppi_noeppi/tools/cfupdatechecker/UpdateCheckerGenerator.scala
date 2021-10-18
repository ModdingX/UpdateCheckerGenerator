package io.github.noeppi_noeppi.tools.cfupdatechecker

import com.google.gson.JsonObject
import io.github.noeppi_noeppi.tools.cfupdatechecker.cache.FileCache
import io.github.noeppi_noeppi.tools.cfupdatechecker.curse.{CurseAPI, CurseFile}
import io.github.noeppi_noeppi.tools.cfupdatechecker.version.VersionResolver

import java.time.LocalDateTime

object UpdateCheckerGenerator {

  private val MAX_TYPE_RECOMMENDED = 1
  private val MAX_TYPE_LATEST = 3
  
  def generateUpdateChecker(projectId: Int, cache: FileCache): (String, JsonObject) = {
    val slug = CurseAPI.slug(projectId)
    System.err.println("Generating version checker for " + slug)

    val filesSorted = CurseAPI.files(projectId, cache).sortBy(_.date)(Ordering[LocalDateTime].reverse)
    val allGameVersions = filesSorted.flatMap(_.gameVersions).toSet
    
    val json = new JsonObject()
    json.addProperty("homepage", "https://www.curseforge.com/minecraft/mc-mods/" + slug)
    
    val promos = new JsonObject
    for (gameVersion <- allGameVersions.toSeq.sorted) {
      val (recommended, latest) = processGameVersionPromos(gameVersion, filesSorted, cache)
      if (recommended.isDefined) promos.addProperty(gameVersion + "-recommended", recommended.get)
      if (latest.isDefined) promos.addProperty(gameVersion + "-latest", latest.get)
    }
    json.add("promos", promos)
    
    for (gameVersion <- allGameVersions.toSeq.sorted) {
      val releases = new JsonObject()
      val files = filesSorted.filter(_.gameVersions.contains(gameVersion)).flatMap(file => VersionResolver.getVersion(file, cache).map(v => (file, v)))
      for ((file, version) <- files.reverse) {
        releases.addProperty(version, file.changelog)
      }
      json.add(gameVersion, releases)
    }
    (slug, json)
  }
  
  private def processGameVersionPromos(gameVersion: String, allFilesSorted: List[CurseFile], cache: FileCache): (Option[String], Option[String]) = {
    val files = allFilesSorted.filter(_.gameVersions.contains(gameVersion))
    val recommended = files.find(file => file.releaseType <= MAX_TYPE_RECOMMENDED)
    val latest = files.find(file => file.releaseType <= MAX_TYPE_LATEST)
    (recommended.flatMap(file => VersionResolver.getVersion(file, cache)), latest.flatMap(file => VersionResolver.getVersion(file, cache)))
  }
}

package io.github.noeppi_noeppi.tools.cfupdatechecker

import com.google.gson.JsonObject
import io.github.noeppi_noeppi.tools.cfupdatechecker.cache.FileCache
import io.github.noeppi_noeppi.tools.cfupdatechecker.version.VersionResolver
import io.github.noeppi_noeppi.tools.cursewrapper.api.CurseWrapper
import io.github.noeppi_noeppi.tools.cursewrapper.api.request.FileFilter
import io.github.noeppi_noeppi.tools.cursewrapper.api.response.{FileInfo, ModLoader, ReleaseType}

import java.time.Instant
import scala.jdk.CollectionConverters._

object UpdateCheckerGenerator {
  
  def generateUpdateChecker(api: CurseWrapper, projectId: Int, cache: FileCache): (String, JsonObject) = {
    val project = api.getProject(projectId)
    System.err.println("Generating version checker for " + project.slug())

    val filesSorted = api.getFiles(projectId, FileFilter.loader(ModLoader.FORGE)).asScala.toSeq.sortBy(_.fileDate())(Ordering[Instant].reverse)
    val allGameVersions = filesSorted.flatMap(_.gameVersions().asScala).toSet
    
    val json = new JsonObject()
    json.addProperty("homepage", project.website().toString)
    
    val promos = new JsonObject
    for (gameVersion <- allGameVersions.toSeq.sorted) {
      val (recommended, latest) = processGameVersionPromos(gameVersion, filesSorted, cache)
      if (recommended.isDefined) promos.addProperty(gameVersion + "-recommended", recommended.get)
      if (latest.isDefined) promos.addProperty(gameVersion + "-latest", latest.get)
    }
    json.add("promos", promos)
    
    for (gameVersion <- allGameVersions.toSeq.sorted) {
      val releases = new JsonObject()
      val files = filesSorted.filter(_.gameVersions().contains(gameVersion)).flatMap(file => VersionResolver.getVersion(file, cache).map(v => (file, v)))
      for ((file, version) <- files.reverse) {
        releases.addProperty(version, cache.changelog(file.projectId(), file.fileId(), {
          ChangelogProcessor.process(api.getChangelog(file.projectId(), file.fileId()))
        }))
      }
      json.add(gameVersion, releases)
    }
    (project.slug(), json)
  }
  
  private def processGameVersionPromos(gameVersion: String, allFilesSorted: Seq[FileInfo], cache: FileCache): (Option[String], Option[String]) = {
    val files = allFilesSorted.filter(_.gameVersions().contains(gameVersion))
    val recommended = files.find(file => file.releaseType() == ReleaseType.RELEASE)
    val latest = files.headOption
    (
      recommended.flatMap(file => VersionResolver.getVersion(file, cache)),
      latest.flatMap(file => VersionResolver.getVersion(file, cache))
    )
  }
}

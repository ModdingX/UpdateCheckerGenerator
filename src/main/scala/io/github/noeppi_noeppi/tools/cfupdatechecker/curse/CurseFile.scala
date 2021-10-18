package io.github.noeppi_noeppi.tools.cfupdatechecker.curse

import java.net.URL
import java.time.LocalDateTime

case class CurseFile(projectId: Int, fileId: Int, name: String, releaseType: Int, gameVersions: Set[String], date: LocalDateTime, changelog: String) {
  
  def downloadURL(): URL = new URL("https://www.cursemaven.com/curse/maven/O-" + projectId + "/" + fileId + "/O-" + projectId + "-" + fileId + ".jar")

  override def toString: String = s"$name ($projectId/$fileId)"
}

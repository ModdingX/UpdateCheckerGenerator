package io.github.noeppi_noeppi.tools.cfupdatechecker.version

import com.google.gson.{JsonArray, JsonElement, JsonObject}
import com.moandjiezana.toml.Toml
import io.github.noeppi_noeppi.tools.cfupdatechecker.Util
import io.github.noeppi_noeppi.tools.cfupdatechecker.cache.FileCache
import io.github.noeppi_noeppi.tools.cursewrapper.api.response.FileInfo
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

import java.io.{BufferedReader, ByteArrayInputStream, InputStreamReader, StringReader}
import java.net.URL
import java.util.zip.ZipInputStream
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

object VersionResolver {

  private val MANIFEST_REGEX: Regex = "^\\s*Implementation-Version\\s*:\\s*(.*?)\\s*$".r
  
  private val FILE_RESOLVERS: Seq[(String, Array[Byte] => Option[String])] = Seq(
    "META-INF/mods.toml" -> text(versionFromToml),
    "mcmod.info" -> text(versionFromLegacy),
    "META-INF/MANIFEST.MF" -> text(versionFromManifest),
    "module-info.class" -> versionFromModule
  )
  private val FILE_NAMES = FILE_RESOLVERS.map(_._1).toSet
  
  def getVersion(file: FileInfo, cache: FileCache): Option[String] = {
    val resolved = cache.version(file.projectId(), file.fileId(), {
      try {
        getVersionFromMetadata(file)
      } catch {
        case e: Exception =>
          e.printStackTrace()
          "INVALID"
      }
    })
    if (resolved == "INVALID") {
      None
    } else {
      Some(resolved)
    }
  }
  
  private def getVersionFromMetadata(file: FileInfo): String = {
    val zin = new ZipInputStream(downloadURL(file).openStream())
    val dataMap = mutable.Map[String, Array[Byte]]()
    var entry = zin.getNextEntry
    while (entry != null) {
      val name = if (entry.getName.startsWith("/")) entry.getName.substring(1) else entry.getName
      if (FILE_NAMES.contains(name)) dataMap(name) = zin.readAllBytes()
      entry = zin.getNextEntry
    }
    zin.close()
    var storedEx: Exception = null
    for ((name, func) <- FILE_RESOLVERS if dataMap.contains(name); data = dataMap(name)) {
      try {
        func(data) match {
          case Some(version) => return version
          case None =>
        }
      } catch {
        case e: Exception if storedEx == null => storedEx = e
      }
    }
    throw new RuntimeException("Failed to resolve version for file " + file, storedEx)
  }

  def downloadURL(file: FileInfo): URL = new URL("https://www.cursemaven.com/curse/maven/O-" + file.projectId() + "/" + file.fileId() + "/O-" + file.projectId() + "-" + file.fileId() + ".jar")
  
  private def versionFromToml(file: String): Option[String] = {
    val toml = new Toml().read(new StringReader(file))
    val tables = toml.getTables("mods").asScala.toSeq
    if (tables.isEmpty) throw new IllegalStateException("No mods in mods.toml")
    if (tables.size != 1) throw new IllegalStateException("Multiple mods in mods.toml")
    val version = tables.head.getString("version").strip()
    if (version.startsWith("$")) return None // Propagate to the manifest
    Some(version)
  }
  
  private def versionFromLegacy(file: String): Option[String] = {
    val mod = Util.GSON.fromJson(file, classOf[JsonElement]) match {
      case obj: JsonObject => obj
      case array: JsonArray if array.size == 1 => array.get(0).getAsJsonObject
      case array: JsonArray if array.size == 0 => throw new IllegalStateException("No mods in mcmod.info")
      case _: JsonArray => throw new IllegalStateException("Multiple mods in mcmod.info")
      case _ => throw new IllegalStateException("Invalid mcmod.info file")
    }
    val version = mod.get("version").getAsString.strip()
    if (version.startsWith("$")) return None // Propagate to the manifest
    Some(version)
  }
  
  private def versionFromManifest(file: String): Option[String] = {
    file.split("\n").flatMap {
      case MANIFEST_REGEX(v) => Some(v.strip())
      case _ => None
    }.headOption
  }
  
  private def versionFromModule(file: Array[Byte]): Option[String] = {
    val cls = new ClassReader(file)
    val node = new ClassNode()
    cls.accept(node, 0)
    if (node.module == null || node.module.version == null) return None
    Some(node.module.version)
  }

  private def text(func: String => Option[String]): Array[Byte] => Option[String] = {
    data => {
      val reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)))
      val str = reader.lines().toArray.toList.map(_.toString).mkString("\n")
      reader.close()
      func(str)
    }
  }
}

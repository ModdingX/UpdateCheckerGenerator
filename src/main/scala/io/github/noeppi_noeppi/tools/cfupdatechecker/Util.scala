package io.github.noeppi_noeppi.tools.cfupdatechecker

import com.google.gson.{Gson, GsonBuilder}
import joptsimple.util.EnumConverter

import java.util
import scala.jdk.CollectionConverters._
import scala.reflect.{ClassTag, classTag}

object Util {
  
  val GSON: Gson = {
    val builder = new GsonBuilder()
    builder.disableHtmlEscaping()
    builder.setPrettyPrinting()
    builder.create()
  }
  
  val INTERNAL: Gson = {
    val builder = new GsonBuilder()
    builder.disableHtmlEscaping()
    builder.create()
  }
  
  def exit(code: Int): Nothing = {
    System.exit(code)
    throw new Error("System.exit returned.")
  }
  
  def enum[T <: Enum[T] : ClassTag]: EnumConverter[T] = new ConcreteEnumConverter(classTag[T].runtimeClass.asInstanceOf[Class[T]])
}

class ConcreteEnumConverter[T <: Enum[T]](clazz: Class[T]) extends EnumConverter[T](clazz) {

  override def valuePattern(): String = {
    util.EnumSet.allOf(valueType()).asScala.map(_.name().toLowerCase).mkString("|")
  }
}



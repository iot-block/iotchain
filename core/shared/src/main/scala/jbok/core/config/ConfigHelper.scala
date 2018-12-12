package jbok.core.config
import cats.data.NonEmptyList
import com.typesafe.config._

import scala.collection.JavaConverters._

object ConfigHelper {
  val minCellLen = 16
  val maxCellLen = 48

  private val system = ConfigFactory.systemProperties()

  private val resource = ConfigFactory.parseResources("reference.conf")

  val reference: Config =
    resource
      .resolveWith(resource.withFallback(system))
      .getConfig("jbok")

  def overrideWith(config: Config): Config =
    config
      .withFallback(resource.resolveWith(config.withFallback(resource).withFallback(system)))
      .getConfig("jbok")

  private def pad(str: String, maxLen: Int): String =
    if (str.length > maxLen) {
      s"${str.take(maxLen - 3)}..."
    } else {
      s"${str.padTo(maxLen, ' ')}"
    }

  final case class Help(rows: List[ConfigItem]) {
    def render: String =
      rows
        .groupBy(_.names.head)
        .toList
        .map {
          case (key, items) =>
            ConfigGroup(key, items).render
        }
        .mkString("\n")
  }

  final case class ConfigGroup(key: String, items: List[ConfigItem]) {
    val maxNameLen   = items.map(_.name.length).max max minCellLen min maxCellLen
    val maxValueLen  = items.map(_.value.length).max max minCellLen min maxCellLen
    val maxDescLen   = items.map(_.desc.length).max max minCellLen min maxCellLen
    val maxOriginLen = items.map(_.origin.length).max max minCellLen min maxCellLen

    def render: String = {
      val header = List(
        pad(s"${key} config", maxNameLen),
        pad("value", maxValueLen),
        pad("description", maxDescLen),
        pad("origin", maxOriginLen),
      ).mkString("| ", " | ", " |")
      val hr = "+" + ("-" * (header.length - 2)) + "+"
      val sb = StringBuilder.newBuilder
      sb ++= hr + "\n"
      sb ++= header + "\n"
      sb ++= hr + "\n"
      sb ++= items.map(_.render(maxNameLen, maxValueLen, maxDescLen, maxOriginLen)).mkString("\n") + "\n"
      sb ++= hr + "\n"
      sb.mkString
    }
  }

  final case class ConfigItem(name: String, value: String, desc: String, origin: String) {
    val names: NonEmptyList[String] = name.split("\\.") match {
      case Array(head) => NonEmptyList.of("*common*", head)
      case xs          => NonEmptyList.fromListUnsafe(xs.toList)
    }

    def render(maxNameLen: Int, maxValueLen: Int, maxDescLen: Int, maxOriginLen: Int): String =
      List(
        pad(name, maxNameLen),
        pad(value, maxValueLen),
        pad(desc, maxDescLen),
        pad(origin, maxOriginLen)
      ).mkString("| ", " | ", " |")
  }

  def printConfig(config: Config): Help = {

    def go(path: String, value: ConfigValue, acc: List[ConfigItem] = Nil): List[ConfigItem] =
      value match {
        case o: ConfigObject =>
          acc ++ o.keySet().asScala.toList.flatMap { key =>
            val nextPath = if (path.isEmpty) key else s"${path}.${key}"
            go(nextPath, o.get(key))
          }

        case _ =>
          ConfigItem(
            path,
            if (value.valueType() == ConfigValueType.NULL) ""
            else value.render(ConfigRenderOptions.concise().setJson(false)),
            value.origin().comments().asScala.mkString(" ").trim,
            value.origin().description().split("@").head
          ) :: acc
      }

    val configItems = go("", config.root())
    Help(configItems)
  }

  def parseConfig(args: List[String]): Either[Throwable, Config] = {
    val pairs = args.grouped(2).toList.map {
      case List(key, _) if !key.startsWith("-") =>
        throw new Exception(s"$key {key} must start with '-'")

      case List(key) =>
        throw new Exception(s"key ${key} has no value")

      case List(key, value) =>
        s"jbok.${key.drop(1)}=${value}"
    }

    Right(
      ConfigFactory.parseString(
        pairs.mkString("\n"),
        ConfigParseOptions.defaults().setOriginDescription("command line")
      )
    )
  }
}

package jbok.core.config
import cats.data.NonEmptyList
import com.typesafe.config._

import scala.collection.JavaConverters._

object ConfigHelper {
  val minCellLen = 20
  val maxCellLen = 60

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
    val maxNameLen  = items.map(_.name.length).max max minCellLen min maxCellLen
    val maxValueLen = items.map(_.value.length).max max minCellLen min maxCellLen
    val maxDescLen  = items.map(_.desc.length).max max minCellLen min maxCellLen

    def render: String = {
      val header = List(
        s"${key} config".padTo(maxNameLen, ' '),
        "value".padTo(maxValueLen, ' '),
        "description".padTo(maxDescLen, ' ')
      ).mkString("| ", " | ", " |")
      val hr = "+" + ("-" * (header.length - 2)) + "+"
      val sb = StringBuilder.newBuilder
      sb ++= hr + "\n"
      sb ++= header + "\n"
      sb ++= hr + "\n"
      sb ++= items.map(_.render(maxNameLen, maxValueLen, maxDescLen)).mkString("\n") + "\n"
      sb ++= hr + "\n"
      sb.mkString
    }
  }

  final case class ConfigItem(name: String, value: String, desc: String, origin: String) {
    val names: NonEmptyList[String] = name.split("\\.") match {
      case Array(head) => NonEmptyList.of("*common*", head)
      case xs          => NonEmptyList.fromListUnsafe(xs.toList)
    }

    def render(maxNameLen: Int, maxValueLen: Int, maxDescLen: Int): String =
      List(
        name.padTo(maxNameLen, ' '),
        value.padTo(maxValueLen, ' '),
        desc.padTo(maxDescLen, ' ')
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
            value.origin().description()
          ) :: acc
      }

    val configItems = go("", config.root())
    Help(configItems)
  }

  def parseConfig(args: List[String]): Either[Throwable, Config] = {
    val strings = args.sliding(2).toList.map {
      case List(key, _) if !key.startsWith("-") =>
        throw new Exception(s"${key} must start with '-'")

      case List(key) =>
        throw new Exception(s"${key} no value provided")

      case List(key, value) =>
        s"${key.drop(1)}=${value}"
    }

    Right(ConfigFactory.parseString(strings.mkString, ConfigParseOptions.defaults().setOriginDescription("cmdline")))
  }
}

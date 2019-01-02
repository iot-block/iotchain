package jbok.app.components

import java.util.UUID

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var}
import jbok.app.views.CustomInput
import org.scalajs.dom.raw.{HTMLInputElement, HTMLTextAreaElement}
import org.scalajs.dom.{Element, Event, KeyboardEvent}

final case class FormEntry(name: String, `type`: String = "text", value: Var[String] = Var("")) {
  val initValue = value.value
}

final case class Form2(entries: Constants[CustomInput], submit: Map[String, CustomInput] => Unit) {
  val entryMap = entries.value.map(x => x.name -> x).toMap

  def clear() = entries.value.foreach(_.clear())

  @binding.dom
  def render(): Binding[Element] =
    <div> 
      {
        entries.map { entry =>
          <div>
            <label for={entry.name}>
              <b>{entry.name}</b>
            </label>
            {entry.render.bind}
          </div>
        }
      }
    </div>
}

final case class Form(entries: Constants[FormEntry], submit: Map[String, String] => Unit, idOpt: Option[String] = None) {
  val id = idOpt getOrElse UUID.randomUUID().toString

  val entryMap = entries.value.map(x => x.name -> x).toMap

  def data = entryMap.mapValues(_.value.value)

  def clear() =
    entryMap.foreach {
      case (_, entry) =>
        entry.value.value = entry.initValue
    }

  val onInputHandler = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement => {
        println(data)
        entryMap.get(input.name).foreach(x => x.value.value = input.value.trim)
      }
      case textarea: HTMLTextAreaElement => {
        entryMap.get(textarea.name).foreach(x => x.value.value = textarea.value.trim)
      }
      case _ =>
    }
  }

  @binding.dom
  def render(): Binding[Element] =
    <div>
      {entries.map { entry =>
      entry.`type` match {
        case "text" | "password" | "search" =>
          <div id={id}>
            <label for={entry.name}>
              <b>
                {entry.name}
              </b>
            </label>
            {
              if (entry.`type`=="text") {
                <input placeholder="" name={entry.name} oninput={onInputHandler} value={entry.value.bind} type="text" />
              } else {
                <input placeholder="" name={entry.name} oninput={onInputHandler} value={entry.value.bind} type={entry.`type`} />
              }
            }
          </div>

        case "textarea" =>
          <div id={id}>
            <label for={entry.name}>
              <b>
                {s"${entry.name} ${entry.`type`}"}
              </b>
            </label>
            <textarea name={entry.name} oninput={onInputHandler} value={entry.value.bind}/>
          </div>
      }
    }}
    </div>
}

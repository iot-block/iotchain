package jbok.app.components

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var}
import org.scalajs.dom.Element

final case class FormEntry(name: String, `type`: String = "text", value: Var[String] = Var("")) {
  val initValue = value.value
}

final case class Form(entries: Constants[Input], submit: Map[String, Input] => Unit) {
  def entryMap = entries.value.map(x => x.name -> x).toMap

  def clear() = entries.value.foreach(_.clear())

  @binding.dom
  def render: Binding[Element] =
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

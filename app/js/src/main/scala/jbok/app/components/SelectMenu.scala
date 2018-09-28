package jbok.app.components

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import org.scalajs.dom._

case class SelectItem(name: String, value: String)

class SelectMenu(title: String) {
  val selected = Var[SelectItem](SelectItem(title, ""))

  @binding.dom
  def render(items: Vars[SelectItem]): Binding[Element] = {
    <div class="select-menu">
      <select>
        <option value="0">{title}</option>
        {
          for {
            item <- items
          } yield {
            <option class="select-option" value={s"${item.value}"}>{item.name}</option>
          }
        }
      </select>
    </div>
  }
}

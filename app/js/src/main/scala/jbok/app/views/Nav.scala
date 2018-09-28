package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import org.scalajs.dom._

object Nav {
  case class Tab(name: String, content: Binding[Node], icon: String)
  case class TabList(tabs: Vars[Tab], selected: Var[Tab])

  @binding.dom
  def render(left: Binding[Node], right: Binding[Node]): Binding[Node] = {
    <nav>
      {left.bind}
      {right.bind}
    </nav>
  }
}

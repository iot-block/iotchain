package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import jbok.app.{IOBinding, JbokClient}
import org.scalajs.dom._

object SimulationsView {
  @binding.dom
  def render(): Binding[Element] = {
    <div></div>
  }
}

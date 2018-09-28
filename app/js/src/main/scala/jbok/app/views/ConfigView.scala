package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import org.scalajs.dom._

object ConfigView {
  @binding.dom
  def render(): Binding[Element] = {
    <div></div>
  }
}


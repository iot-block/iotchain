package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import jbok.app.AppState
import org.scalajs.dom._
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.HTMLInputElement

case class SearchBar(state: AppState) {
  val keyDownHandler = { event: KeyboardEvent =>
    (event.currentTarget, event.keyCode) match {
      case (input: HTMLInputElement, KeyCode.Enter) =>
        input.value.trim match {
          case "" =>
          case text =>
            println(text)
            input.value = ""
        }
      case _ =>
    }
  }

  @binding.dom
  def render: Binding[Element] =
    <input type="search" placeholder="Search.." class="searchbar-input" onkeydown={keyDownHandler}></input>
}

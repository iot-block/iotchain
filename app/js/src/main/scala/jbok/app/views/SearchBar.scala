package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import jbok.app.AppState
import org.scalajs.dom.{Event, _}
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.HTMLInputElement

final case class SearchBar(state: AppState, onPressEnter: Event => Unit) {
  val keyDownHandler = { event: KeyboardEvent =>
    (event.currentTarget, event.keyCode) match {
      case (input: HTMLInputElement, KeyCode.Enter) =>
        onPressEnter(event)
        input.value.trim match {
          case "" =>
          case text if text.startsWith("#") =>
            val blockNumber = text.substring(1)
            state.searchBlockNumber(blockNumber)
          case text if text.startsWith("0x") =>
            val input = text.substring(2)
            if (input.length <= 40) {
              state.searchAccount(input)
            } else {
              state.searchBlockHash(input)
            }
        }
      case _ =>
    }
  }

  @binding.dom
  def render: Binding[Element] =
    <input type="search" placeholder="Search.." class="searchbar-input" onkeydown={keyDownHandler}></input>
}

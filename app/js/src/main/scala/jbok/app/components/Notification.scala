package jbok.app.components

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import org.scalajs.dom.{Element, Event, Node}

object Notification {
  @binding.dom
  def renderInfo(content: Binding[Node], onClick: Event => Unit = (_: Event) => ()): Binding[Element] =
    <div class="notification info">
      <span class="close" onclick={onClick}>&times;</span>
      <strong>Info:</strong>
      <div class="content">{content.bind}</div>
    </div>

  @binding.dom
  def renderSuccess(content: Binding[Element], onClick: Event => Unit = (_: Event) => ()): Binding[Element] =
    <div class="notification success">
      <span class="close" onclick={onClick}>&times;</span>
      <strong>Success:</strong>
      <div class="content">{content.bind}</div>
    </div>

  @binding.dom
  def renderError(content: Binding[Element], onClick: Event => Unit = (_: Event) => ()): Binding[Element] =
    <div class="notification error">
      <span class="close" onclick={onClick}>&times;</span>
      <strong>Error:</strong>
      <div class="content">{content.bind}</div>
    </div>

  @binding.dom
  def renderWarning(content: Binding[Node], onClick: Event => Unit = (_: Event) => ()): Binding[Element] =
    <div class="notification warning">
      <span class="close" onclick={onClick}>&times;</span>
      <strong>Warning:</strong>
      <div class="content">{content.bind}</div>
    </div>
}

package jbok.app.components

import java.util.UUID

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{document, Element, Event}

case class Modal(title: String, body: Binding[Element], onConfirm: () => Unit = { () =>
  }, onCancel: () => Unit = { () =>
  }, idOpt: Option[String] = None) {
  val id = idOpt getOrElse UUID.randomUUID().toString

  val onOpen = (_: Event) => {
    document.getElementById(id).asInstanceOf[HTMLElement].style.display = "block"
  }

  val onClose = (_: Event) => {
    document.getElementById(id).asInstanceOf[HTMLElement].style.display = "none"
  }

  val onConfirmHandler = (_: Event) => {
    onConfirm()
    document.getElementById(id).asInstanceOf[HTMLElement].style.display = "none"
  }

  val onCancelHandler = (_: Event) => {
    onCancel()
    document.getElementById(id).asInstanceOf[HTMLElement].style.display = "none"
  }

  @binding.dom
  val content: Binding[Element] = {
    <div id={s"${id}"} class="modal">
      <div class="modal-content">
        <div class="modal-header">
          <span id={s"close-${id}"} class="modal-close" onclick={onClose}>&times;</span>
          <h2>{title}</h2>
        </div>

        <div class="modal-body">
          {body.bind}
          <button id={s"confirm-${id}"} class="modal-confirm" onclick={onConfirmHandler}>OK</button>
        </div>

        <div class="modal-footer">
          <button id={s"cancel-${id}"} class="modal-cancel" onclick={onCancelHandler}>Cancel</button>
        </div>
      </div>
    </div>
  }

  @binding.dom
  def render(): Binding[Element] =
    <div>
      <button id={s"open-${id}"} class="modal-open" onclick={onOpen}>{title}</button>
      {content.bind}
    </div>
}

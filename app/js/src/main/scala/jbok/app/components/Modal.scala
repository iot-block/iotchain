package jbok.app.components

import java.util.UUID

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{document, Element, Event}

final case class ModalWithButton(title: String, body: Binding[Element], onConfirm: () => Unit = { () =>
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

object Modal {
  sealed trait ModalType {
    val icon: String
  }
  object ModalInfo extends ModalType {
    val icon = "fa-info-circle"
  }
  object ModalSuccess extends ModalType {
    val icon = "fa-check-circle"
  }
  object ModalWarning extends ModalType {
    val icon = "fa-check-circle"
  }
  object ModalError extends ModalType {
    val icon = "fa-times-circle"
  }

  @binding.dom
  def render(modalType: ModalType, content: String): Binding[Element] =
    <div class="modal-info-content row">
      <i class={s"fas fa-fw fa-lg ${modalType.icon}"}></i>
      <div>{content}</div>
    </div>
}

final case class Modal(title: String,
                       body: Binding[Element],
                       visible: Boolean,
                       cancelText: String = "Cancel",
                       okText: String = "Ok",
                       disableCancel: Boolean = false,
                       onOk: Option[Event => Unit] = None,
                       onCancel: Option[Event => Unit] = None) {
  val id = UUID.randomUUID().toString

  val onClose = onCancel getOrElse (
      (_: Event) => document.getElementById(id).asInstanceOf[HTMLElement].style.display = "none"
  )

  val onConfirmHandler = onOk getOrElse (
      (_: Event) => document.getElementById(id).asInstanceOf[HTMLElement].style.display = "none"
  )

  val onCancelHandler = onCancel getOrElse (
      (_: Event) => document.getElementById(id).asInstanceOf[HTMLElement].style.display = "none"
  )

  def show() = document.getElementById(id).asInstanceOf[HTMLElement].style.display = "block"

  @binding.dom
  val content: Binding[Element] = {
    <div id={s"${id}"} class="modal" style={if(visible) {"display: block"} else {"display: none"}}>
      <div class="modal-content">
        <div class="modal-header">
          <span id={s"close-${id}"} class="modal-close" onclick={onClose}>&times;</span>
          <h2>{title}</h2>
        </div>

        <div class="modal-body">
          {body.bind}
        </div>

        <div class="modal-footer">
          <div class="row" style="justify-content:flex-end">
            {
              if (disableCancel) {
                <div/>
              } else {
                <button id={s"cancel-${id}"} class="modal-cancel" onclick={onCancelHandler}>{cancelText}</button>
              }
            }
            <button id={s"confirm-${id}"} class="modal-confirm" onclick={onConfirmHandler}>{okText}</button>
          </div>
        </div>
      </div>
    </div>
  }

  @binding.dom
  def render(): Binding[Element] =
    <div>
      {content.bind}
    </div>
}

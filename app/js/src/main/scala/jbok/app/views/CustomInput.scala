package jbok.app.views

import java.util.UUID

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.core.models.Address
import org.scalajs.dom.Event
import org.scalajs.dom.raw.{Element, HTMLInputElement, HTMLSelectElement, HTMLTextAreaElement}

case class CustomInput(name: String,
                       placeholder: String = "",
                       idOpt: Option[String] = None,
                       validator: String => Boolean = (value: String) => true,
                       `type`: String = "text") {
  private val _value: Var[String]   = Var("")
  private val _Syntax: Var[Boolean] = Var(true)
  val id                            = idOpt getOrElse UUID.randomUUID().toString
  def value: String                 = _value.value
  def isValid: Boolean              = _Syntax.value && _value.value.nonEmpty

  def clear(): Unit = {
    _value.value = ""
    _Syntax.value = true
  }

  private val onInput = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement =>
        _value.value = input.value.trim.toLowerCase
        _Syntax.value = validator(_value.value)
      case textarea: HTMLTextAreaElement =>
        println(s"${_value.value}")
        println(s"${validator(_value.value)}")
        _value.value = textarea.value.trim
        _Syntax.value = validator(_value.value)
      case _ =>
    }
  }

  @binding.dom
  def render: Binding[Element] =
    <div>
      {
        `type` match {
          case "text" =>
            <input id={id} type="text" name={name} placeholder={placeholder} oninput={onInput} value={_value.bind} class={if (_Syntax.bind) "valid" else "invalid"}/>

          case "password" | "search" =>
              <input id={id} type={`type`} name={name} placeholder={placeholder} oninput={onInput} value={_value.bind} class={if (_Syntax.bind) "valid" else "invalid"}/>

          case "textarea" =>
            <textarea id={id} name={name} placeholder={placeholder} oninput={onInput} value={_value.bind} class={if (_Syntax.bind) "valid" else "invalid"}/>
        }
      }
    </div>
}

case class AddressOptionInput(candidates: Vars[Address]) {
  val address: Var[String]              = Var("")
  val otherAddressDisable: Var[Boolean] = Var(false)
  val addressInput                      = CustomInput("address", "address", None, InputValidator.isValidAddress)

  def isValid: Boolean = InputValidator.isValidAddress(address.value)
  def value: String    = address.value

  private val addressOnChange = { event: Event =>
    event.currentTarget match {
      case select: HTMLSelectElement =>
        val v = select.options(select.selectedIndex).value
        otherAddressDisable.value = if (v == "other") {
          address.value = addressInput.value
          false
        } else {
          address.value = v.substring(2)
          println(s"addressopt: ${address.value}, ${isValid}, ${InputValidator.isValidAddress(address.value)}")
          true
        }
      case _ =>
    }
  }

  @binding.dom
  def render: Binding[Element] =
    <div>
      <label for="account">
        <b>
          account:
        </b>
      </label>
      <select name="account" class="autocomplete" onchange={addressOnChange}>
        {
          val accountList = candidates.bind
          for(account<-Constants(accountList: _*)) yield {
            <option value={account.toString}>{account.toString}</option>
          }
        }
        <option value="other" selected={true}>other address</option>
      </select>
      {
        if (!otherAddressDisable.bind) {
          addressInput.render.bind
        } else {
          <div/>
        }
      }
    </div>

}

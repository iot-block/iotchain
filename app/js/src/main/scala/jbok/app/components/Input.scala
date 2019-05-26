package jbok.app.components

import java.util.UUID

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.helper.InputValidator
import jbok.core.models.Address
import org.scalajs.dom.Event
import org.scalajs.dom.raw.{Element, HTMLInputElement, HTMLSelectElement, HTMLTextAreaElement}

final case class Input(
    name: String,
    placeholder: String = "",
    defaultValue: String = "",
    idOpt: Option[String] = None,
    validator: String => Boolean = (input: String) => true,
    onchange: String => Unit = (input: String) => (),
    disabled: Boolean = false,
    `type`: String = "text",
    className: String = ""
) {
  private val _value: Var[String]   = Var(defaultValue)
  private val _Syntax: Var[Boolean] = Var(true)
  val id                            = idOpt getOrElse UUID.randomUUID().toString
  def value: String                 = _value.value
  def isValid: Boolean = {
    _Syntax.value = validator(_value.value)
    _Syntax.value
  }

  def clear(): Unit = {
    _value.value = defaultValue
    _Syntax.value = true
  }

  private val onInput = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement =>
        _value.value = input.value.trim.toLowerCase
        _Syntax.value = validator(_value.value)
        if (_Syntax.value) onchange(_value.value)
      case textarea: HTMLTextAreaElement =>
        _value.value = textarea.value.trim
        _Syntax.value = validator(_value.value)
        if (_Syntax.value) onchange(_value.value)
      case _ =>
    }
  }

  def getSyntaxClass(valid: Boolean): String = if (valid) "valid" else "invalid"

  @binding.dom
  def render: Binding[Element] =
    <div>
      {
        `type` match {
          case "text" =>
            <input id={id} type="text" name={name} disabled={disabled} placeholder={placeholder} oninput={onInput} value={_value.bind} class={s"$className ${getSyntaxClass(_Syntax.bind)}"} required={true}/>
          case "password" | "search" =>
            <input id={id} type={`type`} name={name} disabled={disabled} placeholder={placeholder} oninput={onInput} value={_value.bind} class={s"$className ${getSyntaxClass(_Syntax.bind)}"}/>
          case "textarea" =>
            <textarea id={id} name={name} disabled={disabled} placeholder={placeholder} oninput={onInput} value={_value.bind} class={s"$className ${getSyntaxClass(_Syntax.bind)}"}/>
        }
      }
    </div>
}

final case class AddressOptionInput(candidates: Vars[Address], validator: String => Boolean = InputValidator.isValidAddress, onchange: String => Unit = (addr: String) => ()) {
  val address: Var[String]              = Var("")
  val otherAddressDisable: Var[Boolean] = Var(false)
  val addressInput                      = Input("address", "address", validator = validator, onchange = onchange)

  def isValid: Boolean =
    if (otherAddressDisable.value) {
      validator(address.value)
    } else {
      addressInput.isValid
    }
  def value: String = address.value

  private val addressOnChange = { event: Event =>
    event.currentTarget match {
      case select: HTMLSelectElement =>
        val v = select.options(select.selectedIndex).value
        otherAddressDisable.value = if (v == "other") {
          addressInput.clear()
          address.value = ""
          false
        } else {
          address.value = v
          if (validator(address.value)) onchange(address.value)
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
          val accountList = candidates.all.bind
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

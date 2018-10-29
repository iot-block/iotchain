package jbok.app.views

import java.nio.charset.StandardCharsets

import cats.implicits._
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.{AppState, ContractAddress}
import jbok.app.api.{BlockParam, CallTx, TransactionRequest}
import jbok.core.models.{Account, Address, UInt256}
import org.scalajs.dom.raw.{HTMLInputElement, HTMLSelectElement}
import org.scalajs.dom.{Element, _}
import scodec.bits.ByteVector

import scala.util.matching.Regex

case class CallTxView(state: AppState) {
  val nodeAccounts = Vars.empty[Address]
  val contracts    = Vars.empty[Address]

  val currentId                     = state.currentId.value
  val client                        = currentId.flatMap(state.clients.value.get(_))
  val account: Var[Option[Account]] = Var(None)
  val from: Var[String]             = Var("")
  val fromSyntax: Var[Boolean]      = Var(true)
  val to: Var[String]               = Var("")
  val toSyntax: Var[Boolean]        = Var(true)
  val data: Var[String]             = Var("")
  val dataSyntax: Var[Boolean]      = Var(true)
  val rawResult: Var[ByteVector]    = Var(ByteVector.empty)
  val result: Var[String]           = Var("")

  private def fetch() = {
    val p = for {
      accounts <- client.traverse(_.admin.listAccounts)
      _ = accounts.map(nodeAccounts.value ++= _)
    } yield ()
    p.unsafeToFuture()
  }

  fetch()

  def submit() =
    if (from.value.nonEmpty && fromSyntax.value && toSyntax.value && dataSyntax.value && client.nonEmpty) {
      val fromSubmit = Some(ByteVector.fromValidHex(from.value))
      val toSubmit   = Some(ByteVector.fromValidHex(to.value))
      val dataSubmit = ByteVector.fromValidHex(data.value)
      val callTx     = CallTx(fromSubmit, toSubmit, None, 0, 0, dataSubmit)

      val p = for {
        ret <- client.get.public.call(callTx, BlockParam.Latest)
        _ = rawResult.value = ret
        _ = println(s"result: ${ret.toHex}")
      } yield ()
      p.unsafeToFuture()
    } else {
      println("some error")
    }

  private def updateAccount(address: String) = {
    val p = for {
      a <- client.traverse(_.public.getAccount(Address(ByteVector.fromValidHex(address)), BlockParam.Latest))
      _ = account.value = a
    } yield ()
    p.unsafeToFuture()
  }

  private val fromOnInputHandler = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement =>
        from.value = input.value.trim.toLowerCase
        fromSyntax.value = if (validator.isValidAddress(from.value)) true else false
        if (fromSyntax.value) {
          updateAccount(from.value)
        } else {
          account.value = None
        }
      case _ =>
    }
  }

  private val dataOnInputHandler = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement =>
        data.value = input.value.trim
        dataSyntax.value = if (validator.isValidData(data.value)) true else false
      case _ =>
    }
  }

  val otherAddressDisable: Var[Boolean] = Var(false)
  private val onChangeHandlerFrom = { event: Event =>
    event.currentTarget match {
      case select: HTMLSelectElement =>
        val v = select.options(select.selectedIndex).value
        otherAddressDisable.value = if (v == "other") {
          from.value = ""
          account.value = None
          fromSyntax.value = false
          false
        } else {
          from.value = v.substring(2)
          updateAccount(from.value)
          fromSyntax.value = true
          true
        }
      case _ =>
    }
  }

  private val onChangeHandlerTo = { event: Event =>
    event.currentTarget match {
      case select: HTMLSelectElement =>
        val v = select.options(select.selectedIndex).value
        to.value = v.substring(2)
        toSyntax.value = validator.isValidAddress(to.value)
      case _ =>
    }
  }

  private def decodeByteVector(d: String): String = d match {
    case "string" => {
      val codec = scodec.codecs.string(StandardCharsets.UTF_8)
      codec.decode(rawResult.value.bits).require.value
    }
    case "uint256" => UInt256(rawResult.value).toBigInt.toString
    case _         => rawResult.value.toHex
  }

  private val onChangeHandlerDecode = { event: Event =>
    event.currentTarget match {
      case select: HTMLSelectElement =>
        val v = select.options(select.selectedIndex).value
        result.value = decodeByteVector(v)
      case _ =>
    }
  }

  val onCallHandler = (_: Event) => {
    if (from.value.nonEmpty && fromSyntax.value && toSyntax.value && dataSyntax.value && client.nonEmpty) {
      val fromSubmit = Some(ByteVector.fromValidHex(from.value))
      val toSubmit   = Some(ByteVector.fromValidHex(to.value))
      val dataSubmit = ByteVector.fromValidHex(data.value)
      val callTx     = CallTx(fromSubmit, toSubmit, None, 1, 0, dataSubmit)

      val p = for {
        ret <- client.get.public.call(callTx, BlockParam.Latest)
        _ = rawResult.value = ret
        _ = result.value = ret.toHex
      } yield ()
      p.unsafeToFuture()
    } else {
      println("some error")
    }
  }

  @binding.dom
  def render: Binding[Element] =
    <div>
      {
      <div>
        <label for="account-to-send">Choose a account:</label>
        <select class="autocomplete" onchange={onChangeHandlerFrom}>
          {
          val accountList = nodeAccounts.bind
          for(account<-Constants(accountList: _*)) yield {
          <option value={account.toString}>{account.toString}</option>
        }}
          <option value="other" selected={true}>other address</option>
        </select>
        <input type="text" placeholder="" name="other" oninput={fromOnInputHandler} value={from.bind} class={if(fromSyntax.bind) "valid" else "invalid"} disabled={otherAddressDisable.bind}/>
      </div>
      }

      <div>
        <label for="to">
          <b>
            to
          </b>
        </label>
        <select class="autocomplete" onchange={onChangeHandlerTo}>
          {
            val contractList = state.contractAddress.bind
            for(account<-Constants(contractList: _*)) yield {
              to.value = account.toString.substring(2)
          <option value={account.toString}>{account.toString}</option>
        }}
        </select>
      </div>

      <div>
        <label for="data">
          <b>
            data
          </b>
        </label>
        <input type="text" placeholder="" name="data" oninput={dataOnInputHandler} value={data.bind} class={if(dataSyntax.bind) "valid" else "invalid"} />
      </div>

      <div>
        <label for="result">
          <b>
            result
          </b>
        </label>
        <select class="autocomplete" onchange={onChangeHandlerDecode}>
          <option value="raw">raw</option>
          <option value="string">string</option>
          <option value="uint256">uint256</option>
        </select>
        <input type="text" placeholder="..." name="result" value={result.bind} class="valid" disabled={true}/>
      </div>
      
      <div>
        <button id="call" class="modal-confirm" onclick={onCallHandler}>Call</button>
      </div>
    </div>

}

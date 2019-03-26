package jbok.app.views

import cats.implicits._
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.AppState
import jbok.sdk.api.{BlockParam, CallTx}
import jbok.core.models.{Account, Address}
import org.scalajs.dom.raw._
import org.scalajs.dom.{Element, _}
import org.scalajs.dom
import scodec.bits.ByteVector
import io.circe.parser._
import jbok.sdk.api.{BlockParam, CallTx, TransactionRequest}
import jbok.evm.solidity.ABIDescription.FunctionDescription

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial", "org.wartremover.warts.EitherProjectionPartial"))
final case class CallTxView(state: AppState) {
  val nodeAccounts = Vars.empty[Address]
  val contracts    = Vars.empty[Address]

  val currentId                                           = state.currentId.value
  val client                                              = currentId.flatMap(state.clients.value.get(_))
  val account: Var[Option[Account]]                       = Var(None)
  val to: Var[String]                                     = Var("")
  val toSyntax: Var[Boolean]                              = Var(true)
  val passphase: Var[String]                              = Var("")
  val rawResult: Var[ByteVector]                          = Var(ByteVector.empty)
  val result: Var[String]                                 = Var("")
  val contractAbi: Var[Option[List[FunctionDescription]]] = Var(None)
  val contractSelected: Var[Boolean]                      = Var(false)
  val function: Var[Option[FunctionDescription]]          = Var(None)
  val txType: Var[String]                                 = Var("Send")
  val txStatus: Var[String]                               = Var("")

  val paramInputs: Vars[CustomInput] = Vars.empty[CustomInput]

  private def fetch() = {
    val p = for {
      accounts <- client.traverse(_.personal.listAccounts)
      _ = accounts.map(nodeAccounts.value ++= _)
    } yield ()
    p.unsafeToFuture()
  }

  fetch()

  private def reset() = {
    rawResult.value = ByteVector.empty
    result.value = ""
    txStatus.value = ""

    val element = dom.document.getElementById("decodeSelect")
    element match {
      case x: HTMLSelectElement =>
        x.value = "default"
      case _ => ()
    }
  }

  private val toOnChange = { event: Event =>
    event.currentTarget match {
      case select: HTMLSelectElement =>
        val v = select.options(select.selectedIndex).value
        if (v == "default") {
          contractSelected.value = false
          contractAbi.value = None
        } else {
          to.value = v.substring(2)
          toSyntax.value = InputValidator.isValidAddress(to.value)
          contractSelected.value = true
          contractAbi.value = state.contractInfo.value.find(_.address.toString == v).map {
            _.abi
          }
          function.value = None
        }
      case _ =>
    }
  }

  private val functionOnChange = { event: Event =>
    event.currentTarget match {
      case select: HTMLSelectElement =>
        val v = select.options(select.selectedIndex).value
        if (v == "default") {
          function.value = None
        } else {
          function.value = contractAbi.value.flatMap(_.find(_.name.contains(v)))
          function.value.foreach { f =>
            if (f.stateMutability == "view")
              txType.value = "Call"
            else
              txType.value = "Send"
          }
          function.value.map { f =>
            val t = f.inputs.map { p =>
              val validator = (value: String) => {
                val json = parse(s"[${value}]")
                json.isRight
              }
              CustomInput(p.name.getOrElse(""), p.parameterType.typeString, None, validator)
            }
            paramInputs.value.clear()
            paramInputs.value ++= t
          }
        }
        reset()
      case _ =>
    }
  }

  private val passphaseOnInput = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement => passphase.value = input.value.trim
      case _                       =>
    }
  }

  private def decodeByteVector(d: String): String = d match {
    case "decode" => {
      val result = function.value.get.decode(rawResult.value)
      if (result.isRight) {
        result.right.get.noSpaces
      } else {
        result.left.get.toString
      }
    }
    case _ => rawResult.value.toHex
  }

  private val onChangeHandlerDecode = { event: Event =>
    event.currentTarget match {
      case select: HTMLSelectElement =>
        val v = select.options(select.selectedIndex).value
        result.value = decodeByteVector(v)
    }
  }

  def allReady: Boolean =
    addressOptionInput.isValid &&
      toSyntax.value &&
      client.nonEmpty &&
      function.value.nonEmpty &&
      paramInputs.value.toList.forall(_.isValid)

  val executeOnClick = (_: Event) => {
    if (allReady) {
      val fromSubmit = Some(Address(ByteVector.fromValidHex(addressOptionInput.address.value)))
      val toSubmit   = Some(Address(ByteVector.fromValidHex(to.value)))
      val f          = function.value.get
      val data =
        if (f.inputs.isEmpty) f.methodID
        else {
          f.encode(paramInputs.value.toList.map(_.value).mkString("[", ",", "]")).right.get
        }
      val callTx = CallTx(fromSubmit, toSubmit, None, 1, 0, data)
      if (txType.value == "Call") {
        reset()
        val p = for {
          ret <- client.get.public.call(callTx, BlockParam.Latest)
          _ = rawResult.value = ret
          _ = result.value = ret.toHex
          _ = txStatus.value = "call succcess"
        } yield ()
        txStatus.value = "wait for result..."
        p.unsafeToFuture()
      } else {
        val txRequest =
          TransactionRequest(fromSubmit.get, toSubmit, None, None, None, None, Some(data))
        val password = if (passphase.value.isEmpty) Some("") else Some(passphase.value)
        val p = for {
          account  <- client.get.public.getAccount(fromSubmit.get, BlockParam.Latest)
          gasLimit <- client.get.public.getEstimatedGas(callTx, BlockParam.Latest)
          gasPrice <- client.get.public.getGasPrice
          _ = txStatus.value = s"gas limit: $gasLimit, gas price: $gasPrice"
          txHash <- client.get.personal
            .sendTransaction(
              txRequest.copy(nonce = Some(account.nonce), gasLimit = Some(gasLimit), gasPrice = Some(gasPrice)),
              password)
          stx <- client.get.public.getTransactionByHashFromHistory(txHash)
          _ = stx.map(state.stxs.value(currentId.get).value += _)
          _ = state.receipts.value(currentId.get).value += (txHash -> Var(None))
          _ = txStatus.value = s"send transaction success: ${txHash}"
        } yield ()
        txStatus.value = "estimate gas..."
        p.unsafeToFuture()
      }
    } else {
      println(s"addressOptionInput.isValid: ${addressOptionInput.isValid}")
      println(s"toSyntax.value: ${toSyntax.value}")
      println(s"client.nonEmpty: ${client.nonEmpty}")
      println(s"function.value.nonEmpty: ${function.value.nonEmpty}")
      println(s"paramInputs.value.toList.forall(_.isValid): ${paramInputs.value.toList.forall(_.isValid)}")
    }
  }

  val addressOptionInput = new AddressOptionInput(nodeAccounts)

  @binding.dom
  def render: Binding[Element] =
    <div>
      <div>
        {addressOptionInput.render.bind}
      </div>
      <div>
        <label for="to">
          <b>
            to
          </b>
        </label>
        <select name="to" class="autocomplete" onchange={toOnChange}>
          {
            val contractList = state.contractInfo.all.bind
            for (account <- Constants(contractList.map(_.address): _*)) yield {
              <option value={account.toString}>{account.toString}</option>
            }
          }
          <option value="default" disabled={true} selected={true}>Pick A Contract</option>
        </select>
      </div>

      {
        if(contractSelected.bind) {
          <div>
            <label for="functionSelect">
              <b>
                function
              </b>
            </label>
            {
              contractAbi.bind match {
                case None =>
                  <div/>
                case Some(functions) =>
                  <div>
                    <select name="functionSelect" class="autocomplete" onchange={functionOnChange}>
                      {
                        for (vf <- Constants(functions.filter(_.name != "constructor"): _*)) yield {
                          val fn = vf.name
                          <option value={fn}>{fn}</option>
                        }
                      }
                      <option value="default" disabled={true} selected={true}>Pick A Function</option>
                    </select>
                  </div>
              }
            }
            {
              for (param <- Constants(paramInputs.all.bind.toList: _*)) yield {
                <div>
                  <label for={param.name}>
                    <b>{param.name.stripPrefix("_")}</b>
                  </label>
                  {param.render.bind}
                </div>
              }
            }
          </div>
        } else {
          <div/>
        }
      }

      {
        if (txType.bind == "Call") {
          <div>
            <label for="result">
              <b>
                result
              </b>
            </label>
            <select id="decodeSelect" class="autocomplete" onchange={onChangeHandlerDecode}>
              <option value="default">raw</option>
              <option value="decode">decode</option>
            </select>
            <input type="text" placeholder="Click Call to Get Result" name="result" value={result.bind} class="valid" disabled={true}/>
          </div>
        } else {
          <div>
            <label for="passphase">
              <b>
                passphase
              </b>
            </label>
            <input type="password" name="passphase" oninput={passphaseOnInput} value={passphase.bind}/>
          </div>
        }
      }

      <div>
        status: {txStatus.bind}
      </div>

      <div>
        <button id="call" class="modal-confirm" onclick={executeOnClick} disabled={state.currentId.bind.isEmpty || function.bind.isEmpty}>{txType.bind}</button>
      </div>
    </div>
}

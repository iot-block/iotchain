package jbok.app.views

import cats.implicits._
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.{AppState, Contract}
import jbok.core.models.{Account, Address}
import org.scalajs.dom.raw._
import org.scalajs.dom.{Element, _}
import org.scalajs.dom
import scodec.bits.ByteVector
import io.circe.parser._
import jbok.app.components.{AddressOptionInput, Input, Notification}
import jbok.app.helper.InputValidator
import jbok.core.api.{BlockTag, CallTx, TransactionRequest}
import jbok.evm.solidity.ABIDescription.FunctionDescription

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial", "org.wartremover.warts.EitherProjectionPartial"))
final case class CallTxView(state: AppState) {
  val nodeAccounts = Vars.empty[Address]
  val contracts    = Vars.empty[Address]

  val currentId                                           = state.activeNode.value
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
  val txStatus: Var[Option[String]]                       = Var(None)

  val paramInputs: Vars[Input] = Vars.empty[Input]

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
          contractAbi.value = state.nodes.value
            .get(state.activeNode.value.getOrElse(""))
            .flatMap {
              _.contractsABI.value.find(_.address.toString == v)
            }
            .map(_.abi)
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
              Input(p.name.getOrElse(""), p.parameterType.typeString, validator = validator)
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

  def checkAndGenerateInput() = {
    txStatus.value = None
    for {
      from    <- if (addressOptionInput.isValid) Right(addressOptionInput.value) else Left("not valid from address.")
      to      <- if (toSyntax.value) Right(to.value) else Left("not valid to address.")
      _       <- if (paramInputs.value.toList.forall(_.isValid)) Right(()) else Left("no valid params input.")
      _       <- if (client.nonEmpty) Right(()) else Left("no connect client.")
      abiFunc <- Either.fromOption(function.value, "error contract abi function.")
    } yield execute(from, to, abiFunc)
  }

  def execute(from: String, to: String, functionDescription: FunctionDescription) = {
    val fromSubmit = Some(Address(ByteVector.fromValidHex(from)))
    val toSubmit   = Some(Address(ByteVector.fromValidHex(to)))
    val data =
      if (functionDescription.inputs.isEmpty) functionDescription.methodID
      else {
        functionDescription.encode(paramInputs.value.toList.map(_.value).mkString("[", ",", "]")).right.get
      }
    val callTx = CallTx(fromSubmit, toSubmit, None, 1, 0, data)
    if (txType.value == "Call") {
      reset()
      val p = for {
        ret <- client.get.contract.call(callTx, BlockTag.latest)
        _ = rawResult.value = ret
        _ = result.value = ret.toHex
        _ = txStatus.value = Some("call succcess")
      } yield ()
      txStatus.value = Some("wait for result...")
      p.unsafeToFuture()
    } else {
      val txRequest =
        TransactionRequest(fromSubmit.get, toSubmit, None, None, None, None, Some(data))
      val password = if (passphase.value.isEmpty) Some("") else Some(passphase.value)
      val p = for {
        account <- client.get.account.getAccount(fromSubmit.get, BlockTag.latest)
        _ = txStatus.value = Some("estimate gas...")
        gasLimit <- client.get.contract.getEstimatedGas(callTx, BlockTag.latest)
        gasPrice <- client.get.contract.getGasPrice
        _ = txStatus.value = Some(s"gas limit: $gasLimit, gas price: $gasPrice")
        txHash <- client.get.personal
          .sendTransaction(txRequest.copy(nonce = Some(account.nonce), gasLimit = Some(gasLimit), gasPrice = Some(gasPrice)), password)
        stx <- client.get.transaction.getTx(txHash)
        _ = stx.foreach(state.addStx(currentId.get, _))
        _ = txStatus.value = Some(s"send transaction success: ${txHash}")
      } yield ()
      p.unsafeToFuture()
    }
  }

  val executeOnClick = (_: Event) => checkAndGenerateInput().leftMap(error => txStatus.value = Some(error))

  val addressOptionInput = AddressOptionInput(nodeAccounts)

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
            val contractList = state.nodes.value.get(currentId.getOrElse("")).map(_.contractsABI).getOrElse(Vars.empty[Contract]).all.bind
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

      {
        val onclose = (_: Event) => txStatus.value = None
        @binding.dom def content(status: String):Binding[Element] =
          <div style="padding-left: 10px">{status}</div>
        txStatus.bind match {
          case None => <div/>
//          case Some(status) if status == "sending" =>
//            Notification.renderInfo(content(status), onclose).bind
//          case Some(status) if status == "send success." =>
//            Notification.renderSuccess(content(status), (_: Event) => onclose).bind
          case Some(status) =>
            Notification.renderInfo(content(status), onclose).bind
        }
      }


      <div>
        <button id="call" class="modal-confirm" style={"width: 100%"} onclick={executeOnClick} disabled={state.activeNode.bind.isEmpty || function.bind.isEmpty}>{txType.bind}</button>
      </div>
    </div>
}

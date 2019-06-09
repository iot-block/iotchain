package jbok.app.views

import cats.effect.IO
import cats.implicits._
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import jbok.app.AppState
import jbok.app.components.{AddressOptionInput, Input, Notification}
import jbok.app.execution._
import jbok.app.helper.InputValidator
import jbok.common.math.N
import jbok.common.math.implicits._
import jbok.core.api.BlockTag
import jbok.core.models.{Account, Address}
import org.scalajs.dom.raw.HTMLButtonElement
import org.scalajs.dom.{Element, _}
import scodec.bits.ByteVector

final case class SendTxView(state: AppState) {
  val nodeAccounts = Vars.empty[Address]

  val currentId                     = state.activeNode.value
  val client                        = currentId.flatMap(state.clients.value.get)
  val account: Var[Option[Account]] = Var(None)
  val lock: Var[Boolean]            = Var(false)
  val moreOption: Var[Boolean]      = Var(false)
  val regularGasLimit               = BigInt(21000)

  val fromInput       = AddressOptionInput(nodeAccounts, validator = InputValidator.isValidAddress, onchange = (address: String) => updateAccount(address))
  val toInput         = Input("To", "address", validator = InputValidator.isValidAddress)
  val valueInput      = Input("Value", "0", defaultValue = "0", validator = isValidValue)
  val gasLimitInput   = Input("GasLimit", "21000", defaultValue = regularGasLimit.toString, validator = InputValidator.isValidNumber)
  val dataInput       = Input("Data", "0x...", validator = InputValidator.isValidData, `type` = "textarea")
  val passphraseInput = Input("Passphrase", `type` = "password")

  val statusMessage: Var[Option[String]] = Var(None)

  private def isValidValue(value: String) =
    InputValidator.isValidNumber(value) && account.value.forall(account => N(value) <= account.balance.toN)

  private def updateAccount(address: String) = {
    val p = for {
      a <- client.traverse(_.account.getAccount(Address(ByteVector.fromValidHex(address)), BlockTag.latest))
      _ = account.value = a
    } yield ()
    p.unsafeToFuture()
  }

  private def fetch() = {
    val p = for {
      accounts <- client.traverse(_.personal.listAccounts)
      _ = accounts.map(nodeAccounts.value ++= _)
    } yield ()
    p.unsafeToFuture()
  }
  fetch()

  def checkAndGenerateInput() = {
    statusMessage.value = None
    for {
      _        <- if (!lock.value) Right(()) else Left("please wait for last tx.")
      from     <- if (fromInput.isValid) Right(fromInput.value) else Left("not valid from address.")
      to       <- if (toInput.isValid) Right(toInput.value) else Left("not valid to address.")
      value    <- if (valueInput.isValid) Right(valueInput.value) else Left("not valid send value.")
      gasLimit <- if (gasLimitInput.isValid) Right(gasLimitInput.value) else Left("not valid gas limit.")
      data <- if (!moreOption.value || (moreOption.value && dataInput.value == "")) Right(None)
      else if (moreOption.value && dataInput.isValid) Right(Some(dataInput.value))
      else Left("not valid data.")
      _ <- if (client.nonEmpty) Right(()) else Left("no connect client.")
    } yield sendTx(from, to, value, N(gasLimit), data, passphraseInput.value)
  }

  def sendTx(from: String, to: String, value: String, gasLimit: N, data: Option[String], password: String): Unit = {
    lock.value = true
    statusMessage.value = Some("sending")
    val fromSubmit     = Address(ByteVector.fromValidHex(from))
    val toSubmit       = Some(Address(ByteVector.fromValidHex(to)))
    val valueSubmit    = Some(N(value))
    val gasLimitSubmit = Some(gasLimit)
    val dataSubmit     = data.map(ByteVector.fromValidHex(_))

    client.foreach { client =>
      val p = for {
        gasPrice     <- client.contract.getGasPrice
        hash         <- client.personal.sendTransaction(fromSubmit, password, toSubmit, valueSubmit, gasLimitSubmit, Some(1), None, dataSubmit)
        stxInPool    <- client.transaction.getPendingTx(hash)
        stxInHistory <- client.transaction.getTx(hash)
        stx = stxInPool.fold(stxInHistory)(Some(_))
        _ = stx.fold {
          lock.value = false
          statusMessage.value = Some("send failed.")
        } { tx =>
          state.addStx(state.activeNode.value.getOrElse(""), tx)
          lock.value = false
          statusMessage.value = Some("send success.")
        }
      } yield ()

      p.timeout(state.config.value.clientTimeout)
        .handleErrorWith(e => IO.delay(lock.value = false) >> IO.delay(statusMessage.value = Some(s"send failed: ${e}")))
        .unsafeToFuture()
    }
  }

  val sendOnClick = (_: Event) => checkAndGenerateInput().leftMap(error => statusMessage.value = Some(error))

  private val onClickMoreOption = { event: Event =>
    event.currentTarget match {
      case _: HTMLButtonElement => {
        moreOption.value = !moreOption.value
      }
      case _ =>
    }
  }

  @binding.dom
  def render: Binding[Element] =
    <div>
      {fromInput.render.bind}
      <div>
        <label for="to"><b>to</b></label>
        {toInput.render.bind}
      </div>

      <div>
        <label for="Value">
          <b>
          {
            account.bind match {
              case Some(a) => s"value (max balance: ${a.balance.toString})"
              case _ => "value"
            }
          }
          </b>
        </label>
        {valueInput.render.bind}
      </div>

      <div>
        <label for="GasLimit">
          <b>Gas Limit</b>
          {gasLimitInput.render.bind}
        </label>
      </div>

      <button onclick={onClickMoreOption}>show more option</button>
      {
        if (moreOption.bind) {
          <div>
            <label for="data"><b>data</b></label>
            {dataInput.render.bind}
          </div>
        } else {
          <div/>
        }
      }

      <div>
        <label for="passphase"><b>passphase</b></label>
        {passphraseInput.render.bind}
      </div>

      {
        val onclose = (_: Event) => statusMessage.value = None
        @binding.dom def content(status: String):Binding[Element] =
          <div style="padding-left: 10px">{status}</div>
        statusMessage.bind match {
          case None => <div/>
          case Some(status) if status == "sending" =>
            Notification.renderInfo(content(status), onclose).bind
          case Some(status) if status == "send success." =>
            Notification.renderSuccess(content(status), onclose).bind
          case Some(status) =>
            Notification.renderWarning(content(status), onclose).bind
        }
      }

      <div>
        <button id="send-tx" onclick={sendOnClick} style={"width: 100%"} >send</button>
      </div>

   </div>
}

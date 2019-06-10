package jbok.app.views

import cats.effect.IO
import cats.implicits._
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import jbok.app.AppState
import jbok.app.components.{AddressOptionInput, Input, Notification}
import jbok.app.helper.{ContractAddress, InputValidator}
import jbok.common.math.N
import jbok.core.api.{BlockTag, CallTx}
import jbok.core.models.{Account, Address}
import org.scalajs.dom.{Element, _}
import scodec.bits.ByteVector
import jbok.app.execution._

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial", "org.wartremover.warts.EitherProjectionPartial"))
final case class DeployContractView(state: AppState) {
  val nodeAccounts = Vars.empty[Address]

  val currentId                     = state.activeNode.value
  val client                        = currentId.flatMap(state.clients.value.get(_))
  val account: Var[Option[Account]] = Var(None)
  val lock: Var[Boolean]            = Var(false)
  val passphrase: Var[String]       = Var("")
  val defaultGasLimit               = BigInt(4000000)

  val fromInput = AddressOptionInput(nodeAccounts, validator = InputValidator.isValidAddress, onchange = (address: String) => updateAccount(address))
//  val valueInput = Input("value", "0.0", validator = (value: String) => InputValidator.isValidNumber(value) && account.value.forall(BigInt(value) <= _.balance.toBigInt))
  val dataInput = Input("data", "0x6060604052...", validator = InputValidator.isValidData, `type` = "textarea")
  val password  = Input("passphrase", `type` = "password")

  val statusMessage: Var[Option[String]] = Var(None)

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
      _        <- if (!lock.value) Right(()) else Left("please wait for last deployment.")
      from     <- if (fromInput.isValid) Right(fromInput.value) else Left("not valid from address.")
      data     <- if (dataInput.isValid) Right(dataInput.value) else Left("not valid data address.")
      password <- if (password.isValid) Right(password.value) else Left("not valid password.")
      _        <- if (client.nonEmpty) Right(()) else Left("no connect client.")
    } yield sendTx(from, data, password)
  }

  def sendTx(from: String, data: String, password: String) = {
    lock.value = true
    val fromSubmit = Address(ByteVector.fromValidHex(from))
    val dataSubmit = Some(ByteVector.fromValidHex(data))
    val callTx     = CallTx(Some(fromSubmit), None, None, N(1), N(0), dataSubmit.get)

    client.foreach { client =>
      val p = for {
        account  <- client.account.getAccount(fromSubmit, BlockTag.latest)
        gasPrice <- client.contract.getGasPrice
        gasLimit <- client.contract.getEstimatedGas(callTx, BlockTag.latest)
        stx <- client.personal
          .sendTransaction(fromSubmit, password, None, None, Some(defaultGasLimit), Some(gasPrice.max(N(1))), Some(account.nonce), dataSubmit) >>= client.transaction.getPendingTx
        address = ContractAddress.getContractAddress(fromSubmit, account.nonce)
        _ = stx.fold(
          statusMessage.value = Some("deploy failed.")
        ) { tx =>
          state.addStx(currentId.getOrElse(""), tx)
          state.addContract(currentId.getOrElse(""), address)
          statusMessage.value = Some("deploy success.")
        }
        _ = lock.value = false
      } yield ()

      p.timeout(state.config.value.clientTimeout)
        .handleErrorWith(e => IO.delay(lock.value = false) >> IO.delay(statusMessage.value = Some(s"deploy failed: ${e}")))
        .unsafeToFuture()
    }
  }

  private def updateAccount(address: String) = {
    val p = for {
      a <- client.traverse(_.account.getAccount(Address(ByteVector.fromValidHex(address)), BlockTag.latest))
      _ = account.value = a
    } yield ()
    p.unsafeToFuture()
  }

  val deployOnClick = (_: Event) => checkAndGenerateInput().leftMap(error => statusMessage.value = Some(error))

  @binding.dom
  def render: Binding[Element] =
    <div>
      {fromInput.render.bind}
      <div>
        <label for="data">
          <b>
            Contract Byte Code
          </b>
        </label>
        {dataInput.render.bind}
      </div>

      <div>
        <label for="passphrase">
          <b>
            passphrase
          </b>
        </label>
        {password.render.bind}
      </div>

      {
        val onclose = (_: Event) => statusMessage.value = None
        @binding.dom def content(status: String):Binding[Element] =
          <div style="padding-left: 10px">{status}</div>
        statusMessage.bind match {
          case None => <div/>
          case Some(status) if status == "deploy success." =>
            Notification.renderSuccess(content(status), onclose).bind
          case Some(status) =>
            Notification.renderWarning(content(status), onclose).bind
        }
      }

      <div>
        <button id="deploy-contract" onclick={deployOnClick} style={"width: 100%"} >deploy</button>
      </div>

    </div>
}

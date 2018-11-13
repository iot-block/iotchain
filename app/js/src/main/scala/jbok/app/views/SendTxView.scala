package jbok.app.views

import cats.implicits._
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import jbok.app.api.{BlockParam, TransactionRequest}
import jbok.app.AppState
import jbok.core.models.{Account, Address}
import org.scalajs.dom.raw.HTMLButtonElement
import org.scalajs.dom.{Element, _}
import scodec.bits.ByteVector

case class SendTxView(state: AppState) {
  val nodeAccounts = Vars.empty[Address]

  val currentId                     = state.currentId.value
  val client                        = currentId.flatMap(state.clients.value.get(_))
  val account: Var[Option[Account]] = Var(None)
  val moreOption: Var[Boolean]      = Var(false)

  val fromInput = AddressOptionInput(nodeAccounts)
  val toInput   = CustomInput("To", "address", None, (addr: String) => InputValidator.isValidAddress(addr))
  val valueInput = CustomInput(
    "Value",
    "0.0",
    None,
    (value: String) => InputValidator.isValidNumber(value) && account.value.forall(BigInt(value) <= _.balance.toBigInt))
  val dataInput      = CustomInput("Data", "0x...", None, (data: String) => InputValidator.isValidData(data), "textarea")
  val passphaseInput = CustomInput("Passphase", "", None, (_: String) => true, "password")

  val regularGasLimit = BigInt(21000)
  val callGasLimit    = BigInt(4000000)

  private def fetch() = {
    val p = for {
      accounts <- client.traverse(_.admin.listAccounts)
      _ = accounts.map(nodeAccounts.value ++= _)
    } yield ()
    p.unsafeToFuture()
  }
  fetch()

  def allReady: Boolean =
    fromInput.isValid &&
      toInput.isValid &&
      valueInput.isValid &&
      passphaseInput.isValid &&
      (!moreOption.value || (moreOption.value && dataInput.isValid)) &&
      client.nonEmpty

  def submit() =
    if (allReady) {
      val fromSubmit = Address(ByteVector.fromValidHex(fromInput.value))
      val toSubmit   = Some(Address(ByteVector.fromValidHex(toInput.value)))
      val value      = BigInt(valueInput.value)
      val (gasLimitSubmit, dataSubmit) =
        if (!moreOption.value || (moreOption.value && dataInput.value == "")) Some(regularGasLimit) -> None
        else Some(callGasLimit)                                                                     -> Some(ByteVector.fromValidHex(dataInput.value))
      val txRequest =
        TransactionRequest(fromSubmit, toSubmit, None, gasLimitSubmit, None, None, dataSubmit)
      val password = Some(passphaseInput.value)

      val p = for {
        account  <- client.get.public.getAccount(fromSubmit, BlockParam.Latest)
        gasPrice <- client.get.public.getGasPrice
        hash <- client.get.admin
          .sendTransaction(txRequest.copy(value = Some(account.balance.min(value)),
                                          nonce = Some(account.nonce),
                                          gasPrice = Some(gasPrice)),
                           password)
        stx <- client.get.public.getTransactionByHash(hash)
        _ = stx.map(state.stxs.value(currentId.get).value += _)
        _ = state.receipts.value(currentId.get).value += (hash -> Var(None))
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
        <label for="to">
          <b>
            to
          </b>
        </label>
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

      <button onclick={onClickMoreOption}>show more option</button>
      {
        if (moreOption.bind) {
          <div>
            <label for="data">
              <b>
                data
              </b>
            </label>
            {dataInput.render.bind}
          </div>
        } else {
          <div/>
        }
      }

      <div>
        <label for="passphase">
          <b>
            passphase
          </b>
        </label>
        {passphaseInput.render.bind}
      </div>

   </div>
}

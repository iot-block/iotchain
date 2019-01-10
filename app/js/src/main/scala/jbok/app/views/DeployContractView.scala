package jbok.app.views

import cats.implicits._
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import jbok.app.{AppState, ContractAddress}
import jbok.core.models.{Account, Address}
import jbok.sdk.api.{BlockParam, CallTx, TransactionRequest}
import org.scalajs.dom.raw.HTMLInputElement
import org.scalajs.dom.{Element, _}
import scodec.bits.ByteVector

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial", "org.wartremover.warts.EitherProjectionPartial"))
final case class DeployContractView(state: AppState) {
  val nodeAccounts = Vars.empty[Address]

  val currentId                     = state.currentId.value
  val client                        = currentId.flatMap(state.clients.value.get(_))
  val account: Var[Option[Account]] = Var(None)
  val passphase: Var[String]        = Var("")
  val defaultGasLimit               = BigInt(4000000)

  val fromInput = AddressOptionInput(nodeAccounts)
  val valueInput = CustomInput(
    "value",
    "0.0",
    None,
    (value: String) => InputValidator.isValidNumber(value) && account.value.forall(BigInt(value) <= _.balance.toBigInt))
  val dataInput = CustomInput("data", "0x...", None, InputValidator.isValidData, "textarea")

  private def fetch() = {
    val p = for {
      accounts <- client.traverse(_.personal.listAccounts)
      _ = accounts.map(nodeAccounts.value ++= _)
    } yield ()
    p.unsafeToFuture()
  }

  fetch()

  def allReady: Boolean =
    fromInput.isValid &&
      dataInput.isValid &&
//      valueInput.isValid &&
      client.nonEmpty

  // TODO: value enable when codebyte support payable
  // gasLimit too large will error
  def submit() =
    if (allReady) {
      val fromSubmit  = Address(ByteVector.fromValidHex(fromInput.value))
      val toSubmit    = None
      val valueSubmit = None
//      val valueSubmit = if (valueInput.value.isEmpty) None else Some(BigInt(valueInput.value))
      val dataSubmit = Some(ByteVector.fromValidHex(dataInput.value))
      val txRequest =
        TransactionRequest(fromSubmit, toSubmit, valueSubmit, None, None, None, dataSubmit)
      val password = if (passphase.value.isEmpty) Some("") else Some(passphase.value)
      val callTx   = CallTx(Some(fromSubmit), toSubmit, None, 1, 0, dataSubmit.get)

      val p = for {
        account  <- client.get.public.getAccount(fromSubmit, BlockParam.Latest)
        gasPrice <- client.get.public.getGasPrice
        hash <- client.get.personal.sendTransaction(txRequest.copy(nonce = Some(account.nonce),
                                                                   gasPrice = Some(gasPrice.max(1)),
                                                                   gasLimit = Some(defaultGasLimit)),
                                                    password)
        stx <- client.get.public.getTransactionByHashFromHistory(hash)
        _       = stx.map(state.stxs.value(currentId.get).value += _)
        _       = state.receipts.value(currentId.get).value += (hash -> Var(None))
        address = ContractAddress.getContractAddress(fromSubmit, account.nonce)
//        _       = if (!state.contractInfo.value.toSet.contains(address)) state.contractInfo.value += address
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

  private val passphaseOnInputHandler = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement => passphase.value = input.value.trim
      case _                       =>
    }
  }

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
        <label for="passphase">
          <b>
            passphase
          </b>
        </label>
        <input type="password" placeholder="" name="passphase" oninput={passphaseOnInputHandler} value={passphase.bind} class="valid" />
      </div>

    </div>
}

// value input should enable by contract bytecode (support by payable)
//      <div>
//        <label for="value">
//          <b>
//            {
//            account.bind match {
//              case Some(a) => s"value (max balance: ${a.balance.toString})"
//              case _ => "value"
//            }
//            }
//          </b>
//        </label>
//        {valueInput.render.bind}
//      </div>

package jbok.app.views

import java.nio.charset.StandardCharsets

import cats.implicits._
import com.thoughtworks.binding
import com.thoughtworks.binding.{Binding}
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.AppState
import jbok.app.api.{BlockParam, CallTx, TransactionRequest}
import jbok.core.models.{Account, Address, UInt256}
import jbok.evm.abi.Description
import org.scalajs.dom.raw.{HTMLInputElement, HTMLSelectElement}
import org.scalajs.dom.{Element, _}
import org.scalajs.dom
import scodec.bits.ByteVector

case class CallTxView(state: AppState) {
  val nodeAccounts = Vars.empty[Address]
  val contracts    = Vars.empty[Address]

  val currentId                                    = state.currentId.value
  val client                                       = currentId.flatMap(state.clients.value.get(_))
  val account: Var[Option[Account]]                = Var(None)
  val from: Var[String]                            = Var("")
  val fromSyntax: Var[Boolean]                     = Var(true)
  val to: Var[String]                              = Var("")
  val toSyntax: Var[Boolean]                       = Var(true)
  val passphase: Var[String]                       = Var("")
  val rawResult: Var[ByteVector]                   = Var(ByteVector.empty)
  val result: Var[String]                          = Var("")
  val abi: Var[Option[List[Description.Function]]] = Var(None)
  val contractSelected: Var[Boolean]               = Var(false)
  val function: Var[Option[Description.Function]]  = Var(None)
  val txType: Var[String]                          = Var("Send")
  val txStatus: Var[String]                        = Var("")

  private def fetch() = {
    val p = for {
      accounts <- client.traverse(_.admin.listAccounts)
      _ = accounts.map(nodeAccounts.value ++= _)
    } yield ()
    p.unsafeToFuture()
  }

  fetch()

//  def submit() =
//    if (from.value.nonEmpty && fromSyntax.value && toSyntax.value && dataSyntax.value && client.nonEmpty) {
//      val fromSubmit = Some(ByteVector.fromValidHex(from.value))
//      val toSubmit   = Some(ByteVector.fromValidHex(to.value))
//      val dataSubmit = ByteVector.fromValidHex(data.value)
//      val callTx     = CallTx(fromSubmit, toSubmit, None, 0, 0, dataSubmit)
//
//      val p = for {
//        ret <- client.get.public.call(callTx, BlockParam.Latest)
//        _ = rawResult.value = ret
//        _ = println(s"result: ${ret.toHex}")
//      } yield ()
//      p.unsafeToFuture()
//    } else {
//      println("some error")
//    }

  private def updateAccount(address: String) = {
    val p = for {
      a <- client.traverse(_.public.getAccount(Address(ByteVector.fromValidHex(address)), BlockParam.Latest))
      _ = account.value = a
    } yield ()
    p.unsafeToFuture()
  }

  private val fromOnInput = { event: Event =>
    event.currentTarget match {
      case input: HTMLInputElement =>
        from.value = input.value.trim.toLowerCase
        fromSyntax.value = if (InputValidator.isValidAddress(from.value)) true else false
        if (fromSyntax.value) {
          updateAccount(from.value)
        } else {
          account.value = None
        }
      case _ =>
    }
  }

  val otherAddressDisable: Var[Boolean] = Var(false)
  private val fromOnChange = { event: Event =>
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

  private val toOnChange = { event: Event =>
    event.currentTarget match {
      case select: HTMLSelectElement =>
        val v = select.options(select.selectedIndex).value
        if (v.value == "default") {
          contractSelected.value = false
          abi.value = None
        } else {
          to.value = v.substring(2)
          toSyntax.value = InputValidator.isValidAddress(to.value)
          contractSelected.value = true
          abi.value = state.contractInfo.value.find(_.address.toString == v.value).map {
            _.abi
              .filter(_.isInstanceOf[Description.Function])
              .map(_.asInstanceOf[Description.Function])
              .filter(f => f.`type`.isEmpty || f.`type`.contains("function"))
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
        if (v.value == "default") {
          function.value = None
        } else {
          function.value = abi.value.flatMap(_.find(_.name.contains(v.value)))
          function.value.foreach { f =>
            if (f.stateMutability == "view")
              txType.value = "Call"
            else
              txType.value = "Send"
          }
        }
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

  val executeOnClick = (_: Event) => {
    val inputs = function.value.get.inputs
    val data = if (inputs.size != 0) {
      val params = inputs
        .map(p => p.name)
        .zipWithIndex
        .map {
          case (name, idx) => dom.document.getElementById(s"${idx}-${name}").asInstanceOf[HTMLInputElement].value
        }
        .mkString("[", ",", "]")
      val result = function.value.get.getByteCode(params)
      if (result.isLeft) {
        txStatus.value = result.left.get.toString
        ByteVector.empty
      } else {
        result.right.get
      }
    } else {
      function.value.get.methodID
    }
    if (from.value.nonEmpty && fromSyntax.value && toSyntax.value && client.nonEmpty && account.value.nonEmpty && data.nonEmpty) {
      val fromSubmit = Some(Address(ByteVector.fromValidHex(from.value)))
      val toSubmit   = Some(Address(ByteVector.fromValidHex(to.value)))
      val callTx     = CallTx(fromSubmit, toSubmit, None, 1, 0, data)
      if (txType.value == "Call") {
        val p = for {
          ret <- client.get.public.call(callTx, BlockParam.Latest)
          _ = rawResult.value = ret
          _ = result.value = ret.toHex
        } yield ()
        txStatus.value = "wait for result..."
        p.unsafeToFuture()
      } else {
        val nonceSubmit = account.value.map(_.nonce.toBigInt)
        val txRequest =
          TransactionRequest(fromSubmit.get, toSubmit, None, None, None, nonceSubmit, Some(data))
        val password = if (passphase.value.isEmpty) Some("") else Some(passphase.value)
        val p = for {
          gasLimit <- client.get.public.estimateGas(callTx, BlockParam.Latest)
          gasPrice <- client.get.public.getGasPrice
          _ = txStatus.value = s"gas limit: ${gasLimit}, gas price: ${gasPrice}"
          txHash <- client.get.admin
            .sendTransaction(txRequest.copy(gasLimit = Some(gasLimit), gasPrice = Some(gasPrice)), password)
          _ = txStatus.value = s"sending transaction success: ${txHash}"
        } yield ()
        txStatus.value = "estimate gas..."
        p.unsafeToFuture()
      }
    } else {
      println("some error")
    }
  }

  @binding.dom
  def render: Binding[Element] =
    <div>
      {
      <div>
        <label for="account">
          <b>
            account:
          </b>
        </label>
        <select name="account" class="autocomplete" onchange={fromOnChange}>
          {
            val accountList = nodeAccounts.bind
            for(account<-Constants(accountList: _*)) yield {
              <option value={account.toString}>{account.toString}</option>
            }
          }
          <option value="other" disabled={true} selected={true}>other address</option>
        </select>
        {
          if (!otherAddressDisable.bind) {
            <div>
              <input type="text" placeholder="address" name="other" oninput={fromOnInput} value={from.bind} class={if (fromSyntax.bind) "valid" else "invalid"} />
            </div>
          } else {
            <div/>
          }
        }
      </div>
      }

      <div>
        <label for="to">
          <b>
            to
          </b>
        </label>
        <select name="to" class="autocomplete" onchange={toOnChange}>
          {
            val contractList = state.contractInfo.bind
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
              abi.bind match {
                case None =>
                  <div/>
                case Some(functions) =>
                  <div>
                    <select name="functionSelect" class="autocomplete" onchange={functionOnChange}>
                      {
                        for (vf <- Constants(functions: _*)) yield {
                          val fn = vf.name.getOrElse("undefined")
                          <option value={fn}>{fn}</option>
                        }
                      }
                      <option value="default" disabled={true} selected={true}>Pick A Function</option>
                    </select>
                  </div>
              }
            }
            {
              function.bind match {
                case None =>
                  <div/>
                case Some(description) =>
                  <div>
                    {
                    for (paramIdx <- Constants(description.inputs.zipWithIndex: _*)) yield {
                      val param = paramIdx._1
                      val index = paramIdx._2
                      <div>
                        <label for={param.name}>
                          <b>
                            {param.name.stripPrefix("_")}
                          </b>
                        </label>
                        <input id={s"${index}-${param.name}"} name={param.name} type="text" placeholder={param.`type`}/>
                      </div>
                    }}
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
            <select class="autocomplete" onchange={onChangeHandlerDecode}>
              <option value="raw">raw</option>
              <option value="string">string</option>
              <option value="uint256">uint256</option>
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
        <button id="call" class="modal-confirm" onclick={executeOnClick} disabled={state.currentId.value.isEmpty}>{txType.bind}</button>
      </div>
    </div>

}

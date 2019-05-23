package jbok.app.views

import cats.implicits._
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Var
import fastparse.Parsed.Failure
import jbok.app.{AppState, Contract}
import jbok.app.components.{Input, Notification}
import jbok.app.helper.InputValidator
import jbok.core.models.Address
import jbok.evm.solidity.SolidityParser
import org.scalajs.dom.{Element, Event}
import scodec.bits.ByteVector

final case class WatchView(state: AppState) {
  val currentId = state.activeNode.value

  val contractAddress = Input("Address", "address", validator = InputValidator.isValidAddress)
  val contractCode = Input(
    "Code",
    """pragma solidity ^0.4.0;
      |
      |contract SimpleStorage {
      |    uint storedData;
      |
      |    function set(uint x) public {
      |        storedData = x;
      |    }
      |
      |    function get() public view returns (uint) {
      |        return storedData;
      |    }
      |}
    """.stripMargin,
    `type` = "textarea"
  )
  val statusMessage: Var[Option[String]] = Var(None)

  def checkAndGenerateInput() = {
    statusMessage.value = None
    for {
      address <- if (contractAddress.isValid) Right(contractAddress.value) else Left("not valid contract address.")
      code    <- if (contractCode.isValid) Right(contractCode.value) else Left("not valid contract code.")
    } yield _submit(address, code)
  }

  def _submit(address: String, code: String) = {
    val parseResult     = SolidityParser.parseSource(code)
    val contractAddress = Address(ByteVector.fromValidHex(address))
    parseResult.fold(
      (s, i, e) => {
        val msg = s match {
          case "" =>
            "Position " + e.input.prettyIndex(i) +
              ", found " + Failure.formatTrailing(e.input, i)
          case s => Failure.formatMsg(e.input, List(s -> i), i)
        }
        statusMessage.value = Some(s"parse error: ${msg}")
      },
      (r, i) =>
        currentId.foreach(id => {
          state.nodes.value.get(id).foreach(_.contractsABI.value += Contract(contractAddress, r.contractDefs.last.toABI().methods))
        })
    )
  }

  val watchOnClick = (_: Event) => checkAndGenerateInput().leftMap(error => statusMessage.value = Some(error))

  @binding.dom
  def render: Binding[Element] =
    <div>
      <div>
        <label for="address"><b>Address</b></label>
        {contractAddress.render.bind}
      </div>
      <div>
        <label for="code"><b>Address</b></label>
        {contractCode.render.bind}
      </div>

      {
        val onclose = (_: Event) => statusMessage.value = None
        @binding.dom def content(status: String):Binding[Element] =
          <div style="padding-left: 10px">{status}</div>
        statusMessage.bind match {
          case None => <div/>
          case Some(status) =>
            Notification.renderWarning(content(status), onclose).bind
        }
      }

      <div>
        <button id="watch-contract" onclick={watchOnClick} style={"width: 100%"} >watch</button>
      </div>
    </div>

}

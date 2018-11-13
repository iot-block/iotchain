package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var}
import jbok.app.components.{Form2, Modal}
import jbok.app.{AppState, Contract}
import jbok.core.models.{Account, Address}
import org.scalajs.dom._
import scodec.bits.ByteVector
import jbok.evm.abi.parseContract

case class ContractView(state: AppState) {
  val watchForm = Form2(
    Constants(
      CustomInput("Address", "address", None, (addr: String) => InputValidator.isValidAddress(addr)),
      CustomInput(
        "Abi",
        """
          |[
          |	{
          |		"inputs": [...],
          |		"name": "...",
          |		"type": "function"
          |	},
          | ...
          |]
        """.stripMargin,
        None,
        (abi: String) => InputValidator.isValidABI(abi),
        "textarea"
      )
    ), { data =>
      if (data.values.forall(_.isValid)) {
        state.currentId.value.map { id =>
          val abi     = parseContract(data("Abi").value)
          val address = Address(ByteVector.fromValidHex(data("Address").value))
          if (!state.contractInfo.value.map(_.address).toSet.contains(address))
            state.contractInfo.value += Contract(address, abi.toTry.get)
        }
      }
    }
  )
  def watchOnConfirm(): Unit = {
    watchForm.submit(watchForm.entryMap)
    watchForm.clear()
  }
  def watchOnCancel() =
    watchForm.clear()
  val watchModal = Modal("watch", watchForm.render(), () => watchOnConfirm(), () => watchOnCancel())
  @binding.dom
  def render: Binding[Element] =
    <div>
      {
      val contracts = state.currentId.bind match {
        case Some(id) => state.contracts.value.getOrElse(id, Var(Map.empty[Address, Var[Account]]))
        case _ => Var(Map.empty[Address, Var[Account]])
      }
      <table class="table-view">
        <thead>
          <tr>
            <th>Address</th>
            <th>Balance</th>
            <th>Nonce</th>
            <th>StorageRoot</th>
            <th>CodeHash</th>
          </tr>
        </thead>
        <tbody>
          {
          for ((address, contract) <- Constants(contracts.bind.toList: _*)) yield {
            <tr>
              <td>
                <a>
                  {address.toString}
                </a>
              </td>
              <td>
                {contract.bind.balance.toString}
              </td>
              <td>
                {contract.bind.nonce.toString}
              </td>
              <td>
                {contract.bind.storageRoot.toHex}
              </td>
              <td>
                {contract.bind.codeHash.toHex}
              </td>
            </tr>
          }
          }
        </tbody>
      </table>
      }
      <div class="flex">
        {watchModal.render().bind}
        {
          val client = state.currentId.bind match {
            case Some(id) => state.clients.value.get(id)
            case _ => None
          }
          val contractView = DeployContractView(state)
          def onConfirm(): Unit= {
            contractView.submit()
          }
          def onCancel(): Unit  = {}
          val modal       = Modal("deploy", contractView.render, () => onConfirm(), () => onCancel())
          modal.render().bind
        }
        {
          val client = state.currentId.bind match {
            case Some(id) => state.clients.value.get(id)
            case _ => None
          }
          val callTx = CallTxView(state)
          def onConfirm(): Unit= {}
          def onCancel(): Unit  = {}
          val modal       = Modal("Contract Call", callTx.render, () => onConfirm(), () => onCancel())
          modal.render().bind
        }
      </div>
    </div>
}

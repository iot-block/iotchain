package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.components.{Form, FormEntry, Modal}
import jbok.app.{AppState, SimuClient}
import jbok.core.models.{Account, Address}
import org.scalajs.dom._
import scodec.bits.ByteVector

case class ContractView(state: AppState) {
  val form = Form(
    Constants(FormEntry("address", "text")), { data =>
      state.currentId.value.map { id =>
        val address = Address(ByteVector.fromValidHex(data("address")))
        if (!state.contractAddress.value.toSet.contains(address))
          state.contractAddress.value += address
      }
    }
  )
  def onConfirm() = {
    val data = form.data
    form.submit(data)
    form.clear()
  }
  def onCancel() =
    form.clear()
  val modal = Modal("watch", form.render(), onConfirm, onCancel)
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
        {modal.render().bind}
        {
          val client = state.currentId.bind match {
            case Some(id) => state.clients.value.get(id)
            case _ => None
          }
          val callTx = CallTxView(state)
          def onConfirm(): Unit= {
            callTx.submit()
          }
          def onCancel(): Unit  = {}
          val modal       = Modal("Contract Call", callTx.render, onConfirm, onCancel)
          modal.render().bind
        }
      </div>
    </div>
}

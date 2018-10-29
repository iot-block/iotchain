package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.{AppState, ContractAddress}
import jbok.app.components.Modal
import jbok.core.models._
import org.scalajs.dom._
import scodec.bits.ByteVector

case class TxsView(state: AppState) {
  val transactions = Vars[SignedTransaction]()

  @binding.dom
  def render: Binding[Element] =
    <div>
      {
        val (stxs, receipts) = state.currentId.bind match {
          case Some(id) => {
            state.stxs.value.getOrElse(id, Vars.empty[SignedTransaction]) ->
            state.receipts.value.getOrElse(id, Var(Map.empty[ByteVector, Var[Option[Receipt]]]))
          }
          case _ => {
            Vars.empty[SignedTransaction] ->
            Var(Map.empty[ByteVector, Var[Option[Receipt]]])
          }
        }
        <table class="table-view">
        <thead>
          <tr>
            <th>Tx Hash</th>
            <th>From</th>
            <th>To</th>
            <th>Value</th>
            <th>Status</th>
            <th>Detail</th>
          </tr>
        </thead>
        <tbody>
          {for (tx <- Constants(stxs.bind.toList: _*)) yield {
          <tr>
            <td>
              <a>
                {tx.hash.toHex}
              </a>
            </td>
            <td>
              <a>
                {SignedTransaction.getSender(tx).get.toString}
              </a>
            </td>
            <td>
              {
                if (tx.receivingAddress == Address.empty) {
                  <p>Contract: <a>{ContractAddress.getContractAddress(SignedTransaction.getSender(tx).get, UInt256(tx.nonce)).toString}</a> Created</p>
                } else {
                  <a>{tx.receivingAddress.toString}</a>
                }
              }
            </td>
            <td>
              {tx.value.toString}
            </td>
            <td>
              {
                val default: Option[Receipt] = None
                val r = receipts.bind.getOrElse(tx.hash, Var(default))
                r.bind match {
                  case Some(receipt) => "Success"
                  case None => "Pending"
                }
              }
            </td>
            <td>
              {Modal("view", TxView.render(tx)).render().bind}
            </td>
          </tr>
        }}
        </tbody>
        </table>
      }
      <div class="flex">
      {
        val client = state.currentId.bind match {
          case Some(id) => state.clients.value.get(id)
          case _ => None
        }
        val newTxView = SendTxView(state)
        def onConfirm(): Unit= {
          newTxView.submit()
        }
        def onCancel(): Unit  = {}
        val modal       = Modal("send", newTxView.render, onConfirm, onCancel)
        modal.render().bind
      }
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
        val modal       = Modal("deploy", contractView.render, onConfirm, onCancel)
        modal.render().bind
      }
      </div>
    </div>
}

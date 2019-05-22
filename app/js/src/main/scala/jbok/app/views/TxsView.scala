package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.AppState
import jbok.app.components.{Modal, ModalWithButton}
import jbok.app.helper.{ContractAddress, TableRenderHelper}
import jbok.core.models._
import org.scalajs.dom._
import scodec.bits.ByteVector

final case class TxsView(state: AppState) {
  val transactions = Vars[SignedTransaction]()

  val header: List[String] = List("Tx Hash", "From", "To", "Value", "Status", "Detail")
  val tableRenderHelper    = TableRenderHelper(header)

  val sendVisible: Var[Boolean] = Var(false)
  val sendOnClick = (_: Event) => {
    sendVisible.value = true
  }

  @binding.dom
  def renderTable(stxs: List[SignedTransaction], receipts: Map[ByteVector, Var[Option[Receipt]]]): Binding[Element] =
    <table class="table-view">
      {tableRenderHelper.renderTableHeader.bind}
      <tbody>
        {for (tx <- Constants(stxs: _*)) yield {
        <tr>
          <td>
            <a onclick={(e: Event) => state.searchTxHash(tx.hash.toHex)}>
              {tx.hash.toHex}
            </a>
          </td>
          <td>
            <a onclick={(e: Event) => state.searchAccount(tx.senderAddress.getOrElse("error address").toString)}>
              {tx.senderAddress.getOrElse("error address").toString}
            </a>
          </td>
          <td>
            {
              val contractAddress = tx.senderAddress.map(ContractAddress.getContractAddress(_, UInt256(tx.nonce)).toString).getOrElse("address error.")
              if (tx.receivingAddress == Address.empty) {
                <p>Contract: <a onclick={(e: Event) => state.searchAccount(contractAddress)}>{contractAddress}</a> Created</p>
              } else {
                <a onclick={(e: Event) => state.searchAccount(tx.receivingAddress.toString)}>{tx.receivingAddress.toString}</a>
              }
            }
          </td>
          <td>
            {tx.value.toString}
          </td>
          <td>
            {
              val default: Option[Receipt] = None
              val r = receipts.getOrElse(tx.hash, Var(default))
              r.bind match {
                case Some(receipt) => "Success"
                case None => "Pending"
              }
            }
          </td>
          <td>
            {ModalWithButton("view", TxView.render(state, tx)).render().bind}
          </td>
        </tr>
      }}
      </tbody>
    </table>

  @binding.dom
  def render: Binding[Element] =
    <div>
      {
        if (state.isLoading.loadingReceipts.bind) {
          tableRenderHelper.renderTableSkeleton.bind
        } else {
          state.activeNode.bind.flatMap(id => state.nodes.value.get(id).map(node => node.stxs -> node.receipts)) match {
            case Some((stxs, receipts)) =>
              Constants(stxs.all.bind: _*).all.bind match {
                case stxSeq if stxSeq.nonEmpty => renderTable(stxSeq.toList, receipts.bind).bind
                case _ => tableRenderHelper.renderEmptyTable.bind
              }
            case _ => tableRenderHelper.renderEmptyTable.bind
          }
        }
      }
      <div class="flex">
        <button class="modal-open" onclick={sendOnClick}>Send</button>
        {
          val visible = sendVisible.bind
          val content = state.activeNode.bind match {
            case Some(id) => SendTxView(state).render
            case _ => Modal.render(Modal.ModalInfo, "no node connect. please select node first!")
          }

          val disappear = Some((_: Event) => sendVisible.value = false)
          val modal     = Modal("send", content, visible, onOk = disappear, onCancel = disappear)

          modal.render().bind
        }
      </div>
    </div>
}

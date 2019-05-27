package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var}
import jbok.app.AppState
import jbok.app.components.Modal
import jbok.app.helper.TableRenderHelper
import jbok.core.models.{Account, Address}
import org.scalajs.dom._

final case class ContractView(state: AppState) {
  val contractCallVisible: Var[Boolean] = Var(false)
  val deployVisible: Var[Boolean]       = Var(false)
  val watchVisible: Var[Boolean]        = Var(false)

  val callOnClick = (_: Event) => {
    contractCallVisible.value = true
  }

  val deployOnClick = (_: Event) => {
    deployVisible.value = true
  }

  val watchOnClick = (_: Event) => {
    watchVisible.value = true
  }

  val header: List[String] = List("Address", "Balance", "Nonce", "StorageRoot", "CodeHash")
  val tableRenderHelper    = TableRenderHelper(header)

  @binding.dom
  def renderTable(contracts: Map[Address, Option[Account]]): Binding[Element] =
    <table class="table-view">
      {tableRenderHelper.renderTableHeader.bind}
      <tbody>
        {
        for ((address, contract) <- Constants(contracts.toList: _*)) yield {
          <tr>
            <td>
              <a onclick={(_: Event) => state.searchAccount(address.toString)}>
                {address.toString}
              </a>
            </td>
            <td>
              {contract.map(_.balance.toString).getOrElse("")}
            </td>
            <td>
              {contract.map(_.nonce.toString).getOrElse("")}
            </td>
            <td>
              {contract.map(_.storageRoot.toHex).getOrElse("")}
            </td>
            <td>
              {contract.map(_.codeHash.toHex).getOrElse("")}
            </td>
          </tr>
        }
        }
      </tbody>
    </table>

  @binding.dom
  def render: Binding[Element] =
    <div>
      {
        if (state.isLoading.loadingAccounts.bind) {
          tableRenderHelper.renderTableSkeleton.bind
        } else {
          state.activeNode.bind.flatMap(id => state.nodes.value.get(id).map(_.contracts)) match {
            case Some(contracts) =>
              contracts.bind match {
                case contractMap if contractMap.nonEmpty => renderTable(contractMap).bind
                case _ => tableRenderHelper.renderEmptyTable.bind
              }
            case _ => tableRenderHelper.renderEmptyTable.bind
          }
        }
      }
      <div class="flex">
        <button class="modal-open" onclick={watchOnClick}>Watch</button>
        {
          val visible = watchVisible.bind
          val content = state.activeNode.bind match {
            case Some(id) => WatchView(state).render
            case _ => Modal.render(Modal.ModalInfo, "no node connect. please select node first!")
          }

          val disappear = Some((_: Event) => watchVisible.value = false)
          val modal     = Modal("send", content, visible, onOk = disappear, onCancel = disappear)

          modal.render().bind
        }
        <button class="modal-open" onclick={deployOnClick}>Deploy</button>
        {
          val visible = deployVisible.bind
          val content = state.activeNode.bind match {
            case Some(id) => DeployContractView(state).render
            case _ => Modal.render(Modal.ModalInfo, "no node connect. please select node first!")
          }

          val disappear = Some((_: Event) => deployVisible.value = false)
          val modal     = Modal("deploy", content, visible, onOk = disappear, onCancel = disappear)

          modal.render().bind
        }
        <button class="modal-open" onclick={callOnClick}>Contract Call</button>
        {
          val visible = contractCallVisible.bind
          val content = state.activeNode.bind match {
            case Some(_) => CallTxView(state).render
            case _ => Modal.render(Modal.ModalInfo, "no node connect. please select node first!")
          }
          val onOk = (e: Event) => {
            contractCallVisible.value = false
          }
          val onCancel = (e: Event)  => {
            contractCallVisible.value = false
          }
          val modal       = Modal("Contract Call", content, visible, onOk = Some(onOk), onCancel = Some(onCancel))
          modal.render().bind
        }
      </div>
    </div>
}

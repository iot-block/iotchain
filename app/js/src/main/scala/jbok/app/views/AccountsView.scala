package jbok.app.views

import cats.effect.IO
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var}
import jbok.app.AppState
import jbok.app.components._
import jbok.app.helper.TableRenderHelper
import jbok.core.models.{Account, Address}
import org.scalajs.dom.{Element, _}

final case class AccountsView(state: AppState) {
  val newAccountForm = Form(
    Constants(Input("Password", "password", `type` = "password")), { data =>
      if (data.values.forall(_.isValid))
        state.activeNode.value.map { id =>
          val p = for {
            address <- state.clients.value(id).personal.newAccount(data("Password").value)
            _ = state.addAddress(id, address)
          } yield address
          p.unsafeToFuture()
        }
    }
  )

  val newAccountVisible: Var[Boolean] = Var(false)
  val newAccountClick = (_: Event) => {
    newAccountVisible.value = true
  }

  val header: List[String] = List("Address", "Balance", "Nonce", "StorageRoot", "CodeHash")
  val tableRenderHelper    = TableRenderHelper(header)

  @binding.dom
  def renderTable(accountMap: Map[Address, Option[Account]]): Binding[Element] =
    <table>
      {tableRenderHelper.renderTableHeader.bind}
      <tbody>
        {for ((address, account) <- Constants(accountMap.toList: _*)) yield {
        <tr>
          <td>
            <a onclick={(e: Event) => state.searchAccount(address.toString)}>
              {address.toString}
            </a>
          </td>
          <td>
            {account.map(_.balance.toString).getOrElse("")}
          </td>
          <td>
            {account.map(_.nonce.toString).getOrElse("")}
          </td>
          <td>
            {account.map(_.storageRoot.toHex).getOrElse("")}
          </td>
          <td>
            {account.map(_.codeHash.toHex).getOrElse("")}
          </td>
        </tr>
      }}
      </tbody>
    </table>

  @binding.dom
  def render: Binding[Element] =
    <div>
      {
        if (state.isLoading.loadingAccounts.bind) {
          tableRenderHelper.renderTableSkeleton.bind
        } else {
          state.activeNode.bind.flatMap(id => state.nodes.value.get(id).map(_.addresses)) match {
            case Some(accounts) =>
              accounts.bind match {
                case accountMap if accountMap.nonEmpty => renderTable(accountMap).bind
                case _ => tableRenderHelper.renderEmptyTable.bind
              }
            case _ => tableRenderHelper.renderEmptyTable.bind
          }
        }
      }
      <div class="flex">
        <button class="modal-open" onclick={newAccountClick}>new account</button>
        {
          val visible = newAccountVisible.bind
          val content = state.activeNode.bind match {
            case Some(id) => newAccountForm.render
            case _ => Modal.render(Modal.ModalInfo, "no node connect. please select connect node and retry.")
          }

          val onOk = (_: Event) => {
            newAccountForm.submit(newAccountForm.entryMap)
            newAccountForm.clear()
            newAccountVisible.value = false
          }
          val onCancel = (_: Event) => {
            newAccountForm.clear()
            newAccountVisible.value = false
          }
          val modal     = Modal("new account", content, visible, onOk = Some(onOk), onCancel = Some(onCancel))
          modal.render().bind
        }
      </div>
    </div>

}

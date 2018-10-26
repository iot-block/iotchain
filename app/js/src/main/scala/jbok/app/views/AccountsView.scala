package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.components.{Form, FormEntry, Modal}
import jbok.app.{AppState, SimuClient}
import jbok.core.models.{Account, Address}
import org.scalajs.dom._
import scodec.bits.ByteVector

case class AccountsView(state: AppState) {
  val newAccountForm = Form(
    Constants(FormEntry("password", "password")), { data =>
      state.currentId.value.map { id =>
        val p = for {
          address <- state.clients.value(id).admin.newAccount(data("password"))
          _ = if (state.addressInNode.value.contains(id)) state.addressInNode.value(id).value += address
          else state.addressInNode.value += (id -> Vars(address))
        } yield address
        p.unsafeToFuture()
      }
    }
  )
  def onConfirm() = {
    val data = newAccountForm.data
    newAccountForm.submit(data)
    newAccountForm.clear()
  }
  def onCancel() =
    newAccountForm.clear()
  val newAccountModal = Modal("new account", newAccountForm.render(), onConfirm, onCancel)

  val getCoinForm = Form(
    Constants(FormEntry("address")), { data =>
      val p = for {
        sc <- SimuClient(state.config.value.uri)
        address = Address(ByteVector.fromValidHex(data("address")))
        _ <- sc.simulation.getCoin(address, BigInt("100000000"))
      } yield ()
      p.unsafeToFuture()
    }
  )
  def onConfirm2() = {
    val data = getCoinForm.data
    getCoinForm.submit(data)
    getCoinForm.clear()
  }
  def onCancel2() =
    getCoinForm.clear()
  val getCoinModal = Modal("get coins", getCoinForm.render(), onConfirm2, onCancel2)

  @binding.dom
  def render: Binding[Element] =
    <div>
      {
        val accounts = state.currentId.bind match {
          case Some(id) => state.accounts.value.getOrElse(id, Var(Map.empty[Address, Var[Account]]))
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
            for ((address, account) <- Constants(accounts.bind.toList: _*)) yield {
              <tr>
                <td>
                  <a>
                    {address.toString}
                  </a>
                </td>
                <td>
                  {account.bind.balance.toString}
                </td>
                <td>
                  {account.bind.nonce.toString}
                </td>
                <td>
                  {account.bind.storageRoot.toHex}
                </td>
                <td>
                  {account.bind.codeHash.toHex}
                </td>
              </tr>
            }
          }
        </tbody>
        </table>
      }
      <div class="flex">
        {newAccountModal.render().bind}
        {getCoinModal.render().bind}
      </div>
    </div>

}

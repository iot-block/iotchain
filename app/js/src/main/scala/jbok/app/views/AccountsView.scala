package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Var, Vars}
import jbok.app.components.{Form2, Modal}
import jbok.app.{AppState, SimuClient}
import jbok.core.models.{Account, Address}
import org.scalajs.dom.{Element, _}
import scodec.bits.ByteVector

case class AccountsView(state: AppState) {
  val newAccountForm = Form2(
    Constants(CustomInput("Password", "password", None, (addr: String) => true, "password")), { data =>
      if (data.values.forall(_.isValid))
        state.currentId.value.map { id =>
          val p = for {
            address <- state.clients.value(id).admin.newAccount(data("Password").value)
            _ = if (state.addressInNode.value.contains(id)) state.addressInNode.value(id).value += address
            else state.addressInNode.value += (id -> Vars(address))
          } yield address
          p.unsafeToFuture()
        }
    }
  )
  def onConfirm(): Unit = {
    newAccountForm.submit(newAccountForm.entryMap)
    newAccountForm.clear()
  }
  def onCancel(): Unit =
    newAccountForm.clear()
  val newAccountModal = Modal("new account", newAccountForm.render(), () => onConfirm(), () => onCancel())

  val getCoinForm = Form2(
    Constants(CustomInput("Address", "address", None, (addr: String) => InputValidator.isValidAddress(addr))), { data =>
      if (data.values.forall(_.isValid)) {
        val p = for {
          sc <- SimuClient(state.config.value.uri)
          address = Address(ByteVector.fromValidHex(data("Address").value))
          _ <- sc.simulation.getCoin(address, BigInt("100000000"))
        } yield ()
        p.unsafeToFuture()
      }
    }
  )

  def onConfirm2(): Unit = {
    getCoinForm.submit(getCoinForm.entryMap)
    getCoinForm.clear()
  }
  def onCancel2(): Unit =
    getCoinForm.clear()

  val getCoinModal = Modal("get conins", getCoinForm.render(), () => onConfirm2(), () => onCancel2())

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
                  <a onclick={state.hrefHandler} type="address">
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

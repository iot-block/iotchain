package jbok.app

import java.net.URI

import com.thoughtworks.binding.Binding._
import com.thoughtworks.binding.Binding.Var
import com.thoughtworks.binding._
import jbok.app.components.{SelectItem, SelectMenu, Spinner}
import jbok.app.views.Nav.{Tab, TabList}
import jbok.app.views._
import jbok.common.execution._
import org.scalajs.dom._
import jbok.core.models.Address
import jbok.sdk.api.BlockParam
import org.scalajs.dom.raw.HTMLAnchorElement
import scodec.bits.ByteVector

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@JSImport("css/normalize.css", JSImport.Namespace)
@js.native
object NormalizeCss extends js.Object

@JSImport("css/app.css", JSImport.Namespace)
@js.native
object AppCss extends js.Object

object JbokApp {
  val normalizeCss = NormalizeCss
  val appCss       = AppCss

  val uri = new URI(s"ws://localhost:8888")

  val selectMenu =
    new SelectMenu("please select max").render(Vars(SelectItem("50", "50"), SelectItem("100", "100")))

  val config = AppConfig.default
  val state  = AppState(Var(config), hrefHandler = (event: Event) => handleHref(event))
  state.init()

  val nodeSelect       = new NodeSelect(state).render
  val statusView       = StatusView(state).render
  val accountsView     = AccountsView(state).render
  val blocksView       = BlocksView(state).render
  val transactionsView = TxsView(state).render
  val simulationsView  = SimulationsView.render()
  val accountView      = AccountView(state).render
  val blockView        = BlockView(state).render
  val contractView     = ContractView(state).render
  val configView       = ConfigView(state).render()

  val accountsTab = Tab("Accounts", Var(accountsView), "fa-user-circle")
  val blocksTab   = Tab("Blocks", Var(blocksView), "fa-th-large")
  val txsTab      = Tab("Transactions", Var(transactionsView), "fa-arrow-circle-right")
  val contractTab = Tab("Contract", Var(contractView), "fa-file-contract")
  val configTab   = Tab("", Var(configView), "fa-cogs")
  val tabs: Vars[Tab] = Vars(
    accountsTab,
    blocksTab,
    txsTab,
    contractTab,
    configTab,
  )

  def handleHref: Event => Unit =
    (event: Event) => {
      event.target match {
        case button: Element if button.id == "block-back" =>
          tabList.selected.value.content.value_=(blocksView)
        case button: Element if button.id == "account-back" =>
          tabList.selected.value.content.value_=(accountsView)
        case link: HTMLAnchorElement if link.`type` == "address" =>
          println("in address href")
          val address = Address(ByteVector.fromValidHex(link.text.trim.substring(2)))
          state.currentId.value
            .flatMap { id =>
              state.clients.value.get(id)
            }
            .foreach { client =>
              val p = for {
                account <- client.public.getAccount(address, BlockParam.Latest)
                txs     <- client.personal.getAccountTransactions(address, BlockParam.Earliest, BlockParam.Latest)
                _ = state.selectedAccount.value = Some((address, account, txs))
                _ = tabList.selected.value.content.value_=(accountView)
              } yield ()
              p.unsafeToFuture()
            }
        case link: HTMLAnchorElement if link.`type` == "block" =>
          println("in block href")
          val hash = ByteVector.fromValidHex(link.text.trim)
          state.currentId.value
            .flatMap { id =>
              state.clients.value.get(id)
            }
            .foreach { client =>
              val p = for {
                block <- client.public.getBlockByHash(hash)
                _ = state.selectedBlock.value = block
                _ = tabList.selected.value.content.value_=(blockView)
              } yield ()

              p.unsafeToFuture()
            }
        case link: HTMLAnchorElement if link.`type` == "tx" =>
          println("in tx href")
          val hash = ByteVector.fromValidHex(link.text.trim)
          state.currentId.value
            .flatMap { id =>
              state.clients.value.get(id)
            }
            .foreach { client =>
              val p = for {
                block <- client.public.getTransactionByHashFromHistory(hash)
//                _ = state.selectedBlock.value = block
//                _ = tabList.selected.value = blockTab
              } yield ()

              p.unsafeToFuture()
            }
        case _ =>
      }
    }

  val tabList   = TabList(tabs, Var(tabs.value.head))
  val searchBar = SearchBar(state).render

  @dom val left: Binding[Node] =
    <div class="nav-left">
    {
      for {
        tab <- tabList.tabs
      } yield {
        val isSelected = tabList.selected.bind == tab
        <div class={s"tab ${if (isSelected) "selected" else ""}"}
             onclick={_: Event => tabList.selected.value = tab}>
          <i class={s"fas fa-fw fa-lg ${tab.icon}"}></i>
          { tab.name }
        </div>
      }
    }
    </div>

  @dom val right: Binding[Node] =
    <div class="nav-right">
      <div class ="tab searchbar">{nodeSelect.bind}</div>
      <div class="tab searchbar">{searchBar.bind}</div>
    </div>

  val navBar = Nav.render(left, right)

  @dom def render: Binding[BindingSeq[Node]] =
    <header>
      {navBar.bind}
      {statusView.bind}
    </header>
    <main>
    {
      for {
        tab <- tabList.tabs
      } yield {
        val isSelected = tabList.selected.bind == tab
        <div class={s"tab-content ${if (isSelected) "selected" else ""}"}>
          {tab.content.bind.bind}
        </div>
      }
    }
    </main>
    <footer>
      {Copyright.render.bind}
    </footer>

  def main(args: Array[String]): Unit =
    dom.render(document.body, render)
}

package jbok.app

import java.net.URI

import cats.effect.IO
import com.thoughtworks.binding.Binding._
import com.thoughtworks.binding.Binding.Var
import com.thoughtworks.binding._
import jbok.app.components.{SelectItem, SelectMenu, Spinner}
import jbok.app.views.Nav.{Tab, TabList}
import jbok.app.views._
import jbok.common.execution._
import org.scalajs.dom._
import fs2._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.util.{Failure, Success}
import scala.concurrent.duration._

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
  val state  = AppState(Var(config))
  state.init()
//  (for {
//    client <- JbokClient(config.uri)
//    _ = state.client.value = Some(client)
//    _ <- client.status.evalMap(x => IO(if (x) () else state.client.value = None)).compile.drain
//  } yield ()).unsafeToFuture()

  val nodeSelect       = new NodeSelect(state).render
  val statusView       = StatusView(state).render
  val accountsView     = AccountsView(state).render
  val blocksView       = BlocksView(state).render
  val transactionsView = TxsView(state).render
  val simulationsView  = SimulationsView.render()
  val accountView      = AccountView().render
  val blockView        = BlockView2().render
  val contractView     = ContractView(state).render
  val configView       = ConfigView.render()

  val tabs = Vars(
    Tab("Accounts", accountsView, "fa-user-circle"),
    Tab("Blocks", blocksView, "fa-th-large"),
    Tab("Transactions", transactionsView, "fa-arrow-circle-right"),
    Tab("Simulations", simulationsView, "fa-stethoscope"),
    Tab("Account", accountView, "fa-user-circle"),
    Tab("Block", blockView, "fa-square"),
    Tab("Contract", contractView, "fa-file-contract"),
    Tab("", configView, "fa-cogs")
  )

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
          {tab.content.bind}
        </div>
      }
    }
    </main>
    <footer>
      {Copyright.render.bind}
    </footer>

  def task() =
    if (state.update.value) { state.updateTask() }
  org.scalajs.dom.window.setInterval(() => task(), 5000)

  def main(args: Array[String]): Unit =
    dom.render(document.body, render)
}

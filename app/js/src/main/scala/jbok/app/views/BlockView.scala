package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Var, Vars}
import jbok.app.AppState
import jbok.app.views.Nav.Tab
import jbok.core.models._
import org.scalajs.dom._

import scala.scalajs.js.Date

case class BlockView(state: AppState) {

  @binding.dom
  val overview: Binding[Element] =
    <div>
      {
        state.selectedBlock.bind match {
          case Some(block) =>
            <table class="table-view"> {
              val header = block.header
              val size = block.body.transactionList.size
                <tr>
                <th>Height:</th>
                <td>{header.number.toString}
                </td>
              </tr>
                <tr>
                  <th>TimeStamp:</th>
                  <td>{new Date(header.unixTimestamp).toDateString()}</td>
                </tr>
                <tr>
                  <th>Transactions:</th>
                  <td>{s"${size.toLong} transactions in this Block"}</td>
                </tr>
                <tr>
                  <th>This Block Hash:</th>
                  <td>{header.hash.toHex}</td>
                </tr>
                <tr>
                  <th>Parent Hash:</th>
                  <td><a onclick={state.hrefHandler} type="block">{header.parentHash.toHex}</a></td>
                </tr>
                <tr>
                  <th>Mined By:</th>
                  <td>{header.beneficiary.toHex}</td>
                </tr>
                <tr>
                  <th>Difficulty:</th>
                  <td>{header.difficulty.toString}</td>
                </tr>
                <tr>
                  <th>Gas Used:</th>
                  <td>{header.gasUsed.toString}</td>
                </tr>
                <tr>
                  <th>Gas Limit:</th>
                  <td>{header.gasLimit.toString}</td>
                </tr>
//                <tr>
//                  <th>Nonce:</th>
//                  <td>{header.nonce.toHex}</td>
//                </tr>
                <tr>
                  <th>Extra Data:</th>
                  <td>{header.extra.toHex}</td>
                </tr>
              }
            </table>

          case None =>
            <div></div>
        }
      }
    </div>

  @binding.dom
  val txsView: Binding[Element] =
    <div>
      {
        val stxs: List[SignedTransaction] = state.selectedBlock.bind match {
          case Some(b) => b.body.transactionList
          case _       => List.empty[SignedTransaction]
        }
        StxsView.render(stxs, state.hrefHandler).bind
      }
    </div>

  val tabs = Vars(
    Tab("Overview", Var(overview), ""),
    Tab("Transactions", Var(txsView), ""),
  )

  val tabView = new TabsView(
    Tab("Overview", Var(overview), ""),
    Tab("Transactions", Var(txsView), "")
  )

  @binding.dom
  def render: Binding[Element] =
    <div>
      <button id="block-back" class="btn-back" onclick={state.hrefHandler}>back</button>
      {tabView.render.bind}
    </div>
}

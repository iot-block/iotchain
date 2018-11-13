package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Vars
import jbok.app.AppState
import jbok.app.views.Nav.Tab
import jbok.core.models._
import org.scalajs.dom._

import scala.scalajs.js.Date

case class BlockView(state: AppState) {
  @binding.dom
  val overview: Binding[Element] =
    <div>
      <table class="table-view">
        {
          val (block, size): (BlockHeader, Int) = state.selectedBlock.bind match {
            case Some(b) => (b.header, b.body.transactionList.size)
            case _ => (BlockHeader.empty, 0)
          }
          <tr>
            <th>Height:</th>
            <td>{block.number.toString}
            </td>
          </tr>
          <tr>
            <th>TimeStamp:</th>
            <td>{new Date(block.unixTimestamp).toDateString()}</td>
          </tr>
          <tr>
            <th>Transactions:</th>
            <td>{s"${size.toLong} transactions in this Block"}</td>
          </tr>
          <tr>
            <th>This Block Hash:</th>
            <td>{block.hash.toHex}</td>
          </tr>
          <tr>
            <th>Parent Hash:</th>
            <td><a onclick={state.hrefHandler} type="block">{block.parentHash.toHex}</a></td>
          </tr>
          <tr>
            <th>Mined By:</th>
            <td>{block.beneficiary.toHex}</td>
          </tr>
          <tr>
            <th>Difficulty:</th>
            <td>{block.difficulty.toString}</td>
          </tr>
          <tr>
            <th>Gas Used:</th>
            <td>{block.gasUsed.toString}</td>
          </tr>
          <tr>
            <th>Gas Limit:</th>
            <td>{block.gasLimit.toString}</td>
          </tr>
          <tr>
            <th>Nonce:</th>
            <td>{block.nonce.toHex}</td>
          </tr>
          <tr>
            <th>Extra Data:</th>
            <td>{block.extraData.toHex}</td>
          </tr>
        }
      </table>
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
    Tab("Overview", overview, ""),
    Tab("Transactions", txsView, ""),
  )

  val tabView = new TabsView(
    Tab("Overview", overview, ""),
    Tab("Transactions", txsView, "")
  )

  @binding.dom
  def render: Binding[Element] =
    <div>{tabView.render.bind}</div>
}

package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.{Constants, Vars}
import jbok.app.AppState
import jbok.app.helper.TableRenderHelper
import jbok.core.models.Block
import org.scalajs.dom._

final case class BlocksView(state: AppState) {
  val header: List[String] = List("Number", "Hash", "Timestamp", "Gas Used", "Transactions")
  val tableRenderHelper    = TableRenderHelper(header)

  @binding.dom
  def renderTable(blocks: Vars[Block]): Binding[Element] =
    <table class="table-view">
      {tableRenderHelper.renderTableHeader.bind}
      <tbody>
        {Constants(blocks.all.bind.sortBy(_.header.number).reverse: _*).map { block =>
        <tr>
          <td>
            <a onclick={(e: Event) => state.searchBlockNumber(block.header.number.toString)}>
              {block.header.number.toString}
            </a>
          </td>
          <td>
            <a onclick={(e: Event) => state.searchBlockHash(block.header.hash.toHex)}>
              {block.header.hash.toHex}
            </a>
          </td>
          <td>
            {block.header.unixTimestamp.toString}
          </td>
          <td>
            {block.header.gasUsed.toString}
          </td>
          <td>
            {block.body.transactionList.length.toString}
          </td>
        </tr>
      }}
      </tbody>
    </table>

  @binding.dom
  def render: Binding[Element] =
    <div>
      {
        if (state.isLoading.loadingBlocks.bind) {
          tableRenderHelper.renderTableSkeleton.bind
        } else {
          state.activeNode.bind.flatMap(id => state.nodes.value.get(id).map(_.blocks)) match {
            case Some(blocks) => renderTable(blocks.history).bind
            case _ => tableRenderHelper.renderEmptyTable.bind
          }
        }
      }
    </div>
}

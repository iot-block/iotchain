package jbok.app.views

import cats.implicits._
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Constants
import jbok.app.{AppState, BlockHistory}
import jbok.app.components.Modal
import org.scalajs.dom._

case class BlocksView(state: AppState) {
  @binding.dom
  def render: Binding[Element] =
    <div>
      {
        val history = state.currentId.bind match {
          case Some(id) => state.blocks.value.getOrElse(id, BlockHistory())
          case _ => BlockHistory()
        }
        <table class="table-view">
          <thead>
            <tr>
              <th>Number</th>
              <th>Hash</th>
              <th>Timestamp</th>
              <th>Gas Used</th>
              <th>Transactions</th>
              <th>Detail</th>
            </tr>
          </thead>
          <tbody>
            {for (block <- Constants(history.history.bind.toList.sortBy(_.header.number).reverse: _*)) yield {
            <tr>
              <td>
                {block.header.number.toString}
              </td>
              <td>
                <a>
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
              <td>
                {Modal("view", new BlockView(block).render).render().bind}
              </td>
            </tr>
          }}
          </tbody>
        </table>
      }
    </div>

}

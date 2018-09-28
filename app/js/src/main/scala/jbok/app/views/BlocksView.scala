package jbok.app.views

import cats.implicits._
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Vars
import jbok.app.JbokClient
import jbok.app.components.Modal
import jbok.core.models.Block
import org.scalajs.dom._

class BlocksView(client: JbokClient) {
  val N = 10

  val blocks = Vars.empty[Block]

  def fetch() = {
    val p = for {
      n <- client.public.bestBlockNumber
      xs <- (0 until N).map(i => n - i).filter(_ >= 0).toList.traverse(client.public.getBlockByNumber)
      _ = blocks.value ++= xs.flatten
    } yield ()
    p.unsafeToFuture()
  }

  fetch()

  @binding.dom
  def render(blocks: Vars[Block] = blocks): Binding[Element] = {
    <div>
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
          {for (block <- blocks) yield {
          <tr>
            <td>
              {block.header.number.toString}
            </td>
            <td>
              <a>{block.header.hash.toHex}</a>
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
              {Modal("view", BlockView.render(block)).render().bind}
            </td>
          </tr>
        }}
        </tbody>
      </table>
    </div>
  }
}

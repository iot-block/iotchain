package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Var
import io.circe.generic.auto._
import io.circe.syntax._
import jbok.core.models.Block
import org.scalajs.dom._
import jbok.codec.json._

class BlockView(block: Block) {
  val show: Var[Block] = Var(block)

  @binding.dom
  def render: Binding[Element] = {
    val json = show.bind.asJson.spaces2
    <div>
      <p>{s"Block ${show.bind.header.number} (${show.bind.header.hash.toHex})"}</p>
      <textarea>{json}</textarea>
    </div>
  }
}

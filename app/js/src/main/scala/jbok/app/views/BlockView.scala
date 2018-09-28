package jbok.app.views

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import io.circe.generic.auto._
import io.circe.syntax._
import jbok.core.models.Block
import org.scalajs.dom._
import jbok.codec.json._

object BlockView {
  @binding.dom
  def render(block: Block): Binding[Element] = {
    val json = block.asJson.spaces2
    <div>
      <p>{s"Block ${block.header.number} (${block.header.hash.toHex})"}</p>
      <textarea>{json}</textarea>
    </div>
  }
}


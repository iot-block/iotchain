package jbok.app.helper

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Constants
import jbok.app.components.{Empty, Skeleton}
import org.scalajs.dom.Element

final case class TableRenderHelper(header: List[String]) {
  @binding.dom
  val renderTableHeader: Binding[Element] =
    <thead>
      <tr>
        {Constants(header: _*).map { text =>
        <th>{text}</th> }}
      </tr>
    </thead>

  @binding.dom
  val renderEmptyTable: Binding[Element] =
    <div>
      <table>
        {renderTableHeader.bind}
      </table>
      {Empty.render.bind}
    </div>

  @binding.dom
  val renderTableSkeleton: Binding[Element] =
    <div>
      <table>{renderTableHeader.bind}</table>
      {Skeleton.renderTable(3, header.length).bind}
    </div>
}

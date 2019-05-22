package jbok.app.components

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import com.thoughtworks.binding.Binding.Constants
import org.scalajs.dom.Element

object Skeleton {
  val minTableRow = 2
  val minTableCol = 2

  @binding.dom
  def render: Binding[Element] =
    <div></div>

  @binding.dom
  def renderTable(row: Int, column: Int): Binding[Element] = {
    val rowNumber    = row.max(minTableRow)
    val columnNumber = column.max(minTableCol)
    val rowWidth     = f"${100.0 / columnNumber}%.2f"

    <div class="skeleton">
      {
        Constants((1 to rowNumber).toList: _*).map { _ =>
          <div class="row">
          {
            Constants((1 to columnNumber).toList: _*).map { _ =>
              <div class="col" style={s"width: $rowWidth%"}>
                <div class="skeleton-cell"></div>
              </div>
            }
          }
          </div>
        }
      }
    </div>
  }

}

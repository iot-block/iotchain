package jbok.app.views
import com.thoughtworks.binding.{Binding, dom}
import org.scalajs.dom._

object Copyright {
  @dom
  def render: Binding[Node] =
    <div class="copyright">
      <p>App built with ♡ - © 2018 <a>JBOK</a></p>
    </div>
}

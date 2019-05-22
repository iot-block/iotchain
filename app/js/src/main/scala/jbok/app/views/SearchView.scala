package jbok.app.views

import jbok.app.AppState
import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import org.scalajs.dom._

final case class SearchView(state: AppState) {
  @binding.dom
  def render: Binding[Element] =
    <div>
      {state.search.bind match {
        case Some(search) => SearchResult(state, search).render.bind
        case None =>
          <div class="search">
            <h2>Search Result:</h2>
          </div>
      }}
    </div>

}

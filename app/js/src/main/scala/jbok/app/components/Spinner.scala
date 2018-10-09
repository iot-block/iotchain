package jbok.app.components

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import org.scalajs.dom.Element

import scala.util.{Failure, Success, Try}

object Spinner {
  @binding.dom
  def render[A](fb: Binding[Option[Try[A]]]): Binding[Element] = fb.bind match {
    case Some(Success(a)) =>
      <div>{a.toString}</div>
    case Some(Failure(e)) =>
      <div>{e.toString}</div>
    case None =>
      <div class="spinner">
        <div class="rect1"></div>
        <div class="rect2"></div>
        <div class="rect3"></div>
        <div class="rect4"></div>
        <div class="rect5"></div>
      </div>
  }
}

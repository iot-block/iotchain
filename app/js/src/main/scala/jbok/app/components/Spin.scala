package jbok.app.components

import com.thoughtworks.binding
import com.thoughtworks.binding.Binding
import org.scalajs.dom.Element

import scala.util.{Failure, Success, Try}

object Spin {
  @binding.dom
  def render(color: String = "blue"): Binding[Element] =
    <div class="spinner">
        <div class={s"rect1 $color"}></div>
        <div class={s"rect2 $color"}></div>
        <div class={s"rect3 $color"}></div>
        <div class={s"rect4 $color"}></div>
        <div class={s"rect5 $color"}></div>
      </div>

  @binding.dom
  def renderFuture[A](fb: Binding[Option[Try[A]]]): Binding[Element] = fb.bind match {
    case Some(Success(a)) => <div>{a.toString}</div>
    case Some(Failure(e)) => <div>{e.toString}</div>
    case None             => <div>{render().bind}</div>
  }
}

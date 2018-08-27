package jbok.network.common

trait RequestId[A] {
  def id(a: A): Option[String]
}

object RequestId {
  def apply[A](implicit ev: RequestId[A]): RequestId[A] = ev
}

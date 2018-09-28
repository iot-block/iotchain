package jbok.network.common

trait RequestId[A] {
  def id(a: A): Option[String]
}

object RequestId {
  def apply[A](implicit ev: RequestId[A]): RequestId[A] = ev

  def none[A] = new RequestId[A] {
    override def id(a: A): Option[String] = None
  }
}

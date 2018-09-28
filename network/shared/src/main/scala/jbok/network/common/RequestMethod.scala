package jbok.network.common

trait RequestMethod[A] {
  def method(a: A): Option[String]
}

object RequestMethod {
  def apply[A](implicit ev: RequestMethod[A]): RequestMethod[A] = ev

  def none[A]: RequestMethod[A] = new RequestMethod[A] {
    override def method(a: A): Option[String] = None
  }
}

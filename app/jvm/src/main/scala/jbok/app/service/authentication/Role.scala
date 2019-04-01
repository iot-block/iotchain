package jbok.app.service.authentication

import cats.implicits._
import cats.kernel.Eq
import tsec.authorization.{AuthGroup, SimpleAuthEnum}

sealed case class Role(roleRepr: String)
object Role extends SimpleAuthEnum[Role, String] {
  val Administrator: Role = Role("Administrator")
  val Customer: Role      = Role("User")
  val Seller: Role        = Role("Seller")

  implicit val E: Eq[Role] = Eq.fromUniversalEquals[Role]

  def getRepr(t: Role): String = t.roleRepr

  protected val values: AuthGroup[Role] = AuthGroup(Administrator, Customer, Seller)
}


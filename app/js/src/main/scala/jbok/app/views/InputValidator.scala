package jbok.app.views

import jbok.core.models.Account
import jbok.evm.abi

import scala.util.matching.Regex

object InputValidator {
  val number = "0123456789"
  val alpha  = new Regex("[a-z]+")
  val hex    = "0123456789abcdef"

  def isValidAddress(address: String): Boolean = address.forall(hex.contains(_)) && address.length == 40
  def isValidNumber(n: String): Boolean        = n.forall(number.contains(_))
  def isValidValue(value: String, account: Option[Account]): Boolean =
    value.forall(number.contains(_)) && account.forall(_.balance.toBigInt >= BigInt(value))
  def isValidData(data: String): Boolean = data.forall(hex.contains(_))
  def isValidPort(data: String): Boolean =
    data.nonEmpty && data.forall(number.contains(_)) && data.length <= 5 && data.toInt > 1000 && data.toInt < 65535
  def isValidABI(data: String): Boolean = abi.parseContract(data).isRight
}

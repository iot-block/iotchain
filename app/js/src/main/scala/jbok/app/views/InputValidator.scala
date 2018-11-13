package jbok.app.views

import jbok.core.models.Account
import jbok.evm.abi

import scala.util.matching.Regex

object InputValidator {
  val number = "0123456789"
  val alpha  = new Regex("[a-z]+")
  val hex    = "0123456789abcdef"

  private def getHexValue(data: String): String = data match {
    case d if d.startsWith("0x") => d.substring(2)
    case _                       => data
  }

  def isValidAddress(address: String): Boolean = {
    val value = getHexValue(address)
    value.length == 40 && value.forall(hex.contains(_))
  }
  def isValidNumber(n: String): Boolean = n.forall(number.contains(_))
  def isValidValue(value: String, account: Option[Account]): Boolean =
    value.forall(number.contains(_)) && account.forall(_.balance.toBigInt >= BigInt(value))
  def isValidData(data: String): Boolean = {
    val value = getHexValue(data)
    value.length % 2 == 0 && value.forall(hex.contains(_))
  }
  def isValidPort(data: String): Boolean =
    data.nonEmpty && data.forall(number.contains(_)) && data.length <= 5 && data.toInt > 1000 && data.toInt < 65535
  def isValidABI(data: String): Boolean  = abi.parseContract(data).isRight
  def isValidBool(data: String): Boolean = data == "true" || data == "false"
  def isValidBytes(data: String, length: Option[Int] = None): Boolean = {
    val value = data match {
      case d if d.startsWith("0x") => d.substring(2)
      case _                       => data
    }

    val dataValid = data.forall(hex.contains(_))

    dataValid && (if (length.isEmpty)
                    value.length % 2 == 0
                  else
                    value.length == (length.get * 2))

  }
}

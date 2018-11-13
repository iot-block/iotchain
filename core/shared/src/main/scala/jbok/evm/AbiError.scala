package jbok.evm

sealed trait AbiError

case class InvalidType(reason: String) extends AbiError

case class InvalidParam(reason: String) extends AbiError

case class InvalidValue(reason: String) extends AbiError

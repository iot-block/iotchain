package jbok.network.rpc

object RpcValidator {
  type Validation = Either[String, Unit]

  def Valid: Validation                = Right(())
  def Invalid(msg: String): Validation = Left(msg)

  def validate(check: => Boolean, errorMsg: => String): Validation = Either.cond(check, (), errorMsg)
}


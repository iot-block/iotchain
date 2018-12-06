package jbok.crypto.signature

object ECDSAChainIdConvert {
  def getPointSign(chainId: BigInt, recoveryId: BigInt): Option[BigInt] =
    if (recoveryId == chainId * 2 + NEW_NEGATIVE_POINT_SIGN) {
      Some(NEGATIVE_POINT_SIGN)
    } else if (recoveryId == chainId * 2 + NEW_POSITIVE_POINT_SIGN) {
      Some(POSITIVE_POINT_SIGN)
    } else {
      None
    }

  def getRecoveryId(chainId: BigInt, pointSign: BigInt): Option[BigInt] =
    if (pointSign == NEGATIVE_POINT_SIGN) Some(chainId * 2 + NEW_NEGATIVE_POINT_SIGN)
    else if (pointSign == POSITIVE_POINT_SIGN) Some(chainId * 2 + NEW_POSITIVE_POINT_SIGN)
    else None

  def getChainId(recoveryId: BigInt): Option[BigInt] =
    if (recoveryId < NEW_NEGATIVE_POINT_SIGN) None
    else
      Some(
        if (recoveryId % 2 == 0) (recoveryId - NEW_POSITIVE_POINT_SIGN) / 2
        else (recoveryId - NEW_NEGATIVE_POINT_SIGN) / 2)

  val UNCOMPRESSED_INDICATOR_BYTE: Byte     = 0x04
  val UNCOMPRESSED_INDICATOR_STRING: String = "04"
  val NEGATIVE_POINT_SIGN: BigInt           = 27
  val POSITIVE_POINT_SIGN: BigInt           = 28
  val NEW_NEGATIVE_POINT_SIGN: BigInt       = 27
  val NEW_POSITIVE_POINT_SIGN: BigInt       = 28
  val allowedPointSigns                     = Set(NEGATIVE_POINT_SIGN, POSITIVE_POINT_SIGN)
}

package jbok.crypto.signature

object SignatureRecover {
  val negativePointSign: Byte = 27
  val newNegativePointSign: Byte = 35
  val positivePointSign: Byte = 28
  val newPositivePointSign: Byte = 36

  val allowedPointSigns = Set(negativePointSign, positivePointSign)

  def getRecoveredPointSign(pointSign: Byte, chainId: Option[Byte]): Option[Byte] =
    (chainId match {
      case Some(id) =>
        if (pointSign == negativePointSign || pointSign == (id * 2 + newNegativePointSign).toByte) {
          Some(negativePointSign)
        } else if (pointSign == positivePointSign || pointSign == (id * 2 + newPositivePointSign).toByte) {
          Some(positivePointSign)
        } else {
          None
        }
      case None => Some(pointSign)
    }).filter(pointSign => allowedPointSigns.contains(pointSign))


  def pointSign(chainId: Option[Byte], v: Byte): Byte = chainId match {
    case Some(id) if v == negativePointSign => (id * 2 + newNegativePointSign).toByte
    case Some(id) if v == positivePointSign => (id * 2 + newPositivePointSign).toByte
    case None => v
  }
}

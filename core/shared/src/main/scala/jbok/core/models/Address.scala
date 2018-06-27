package jbok.core.models

import scodec.bits.ByteVector

case class Address(bytes: ByteVector) extends AnyVal

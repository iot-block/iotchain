package jbok.codec

package object nibble {
  case class Nibble private (value: Int) extends AnyVal
  implicit def nibbleToInt(nibble: Nibble) = nibble.value

  type Nibbles = List[Nibble]
}

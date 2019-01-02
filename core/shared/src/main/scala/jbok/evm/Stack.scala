package jbok.evm

import jbok.core.models.UInt256

/**
  * [[Stack]] for the EVM.
  *
  * It doesn't handle overflow and underflow errors.
  * Any operations that transcend given stack bounds will return the stack unchanged.
  * Pop will always return zeroes in such case.
  */
class Stack private (private val underlying: List[UInt256], val maxSize: Int) {
  def pop: (UInt256, Stack) = underlying.headOption match {
    case Some(word) =>
      (word, copy(underlying.drop(1)))

    case None =>
      (UInt256.Zero, this)
  }

  def pop(n: Int): (List[UInt256], Stack) = {
    val (popped, left) = underlying.splitAt(n)
    if (popped.length == n)
      (popped, copy(left))
    else
      (List.fill(n)(UInt256.Zero), this)
  }

  def push(word: UInt256): Stack =
    if (underlying.length < maxSize) {
      copy(word :: underlying)
    } else {
      this
    }

  def push(words: List[UInt256]): Stack =
    if (words.length + underlying.length <= maxSize) {
      copy(words.reverse.toList ++ underlying)
    } else {
      this
    }

  def dup(i: Int): Stack =
    if (i < 0 || i >= underlying.length || underlying.length >= maxSize)
      this
    else
      copy(underlying(i) :: underlying)

  /**
    * Swap i-th and the top-most elements of the stack. i=0 is the top-most element (and that would be a no-op)
    */
  def swap(i: Int): Stack =
    underlying.headOption match {
      case None                                        => this
      case Some(_) if i <= 0 || i >= underlying.length => this
      case Some(head)                                  => copy(underlying(i) :: underlying.updated(i, head).drop(1))
    }

  def size: Int =
    underlying.size

  def toList: List[UInt256] =
    underlying

  override def equals(that: Any): Boolean = that match {
    case that: Stack => this.underlying == that.underlying
    case _           => false
  }

  override def hashCode(): Int = underlying.hashCode

  override def toString: String =
    underlying.reverse.mkString("Stack(", ",", ")")

  private def copy(words: List[UInt256]): Stack =
    new Stack(words, maxSize)
}

object Stack {
  val DefaultMaxSize = 1024

  def empty(maxSize: Int = DefaultMaxSize): Stack =
    new Stack(Nil, maxSize)
}

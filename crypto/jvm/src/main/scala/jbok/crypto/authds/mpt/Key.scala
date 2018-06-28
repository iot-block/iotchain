package jbok.crypto.authds.mpt

import scala.annotation.tailrec

object Key {
  @tailrec
  def longestCommonPrefix(a: String, b: String, out: String = ""): String = {
    if (a == "" || b == "" || a(0) != b(0)) out
    else longestCommonPrefix(a.substring(1), b.substring(1), out + a(0))
  }
}

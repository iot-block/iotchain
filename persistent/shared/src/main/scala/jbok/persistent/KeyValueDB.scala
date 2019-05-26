package jbok.persistent

object DBErr {
  case object NotFound extends Exception("NotFound")
}

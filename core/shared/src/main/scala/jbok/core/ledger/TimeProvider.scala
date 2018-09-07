package jbok.core.ledger

import java.time.Instant

trait TimeProvider {
  def getEpochSecond: Long
}

object DefaultTimeProvider extends TimeProvider {
  override def getEpochSecond: Long = Instant.now.getEpochSecond
}

class FakeTimeProvider extends TimeProvider {
  private var timestamp = Instant.now.getEpochSecond

  def advance(seconds: Long): Unit = timestamp += seconds

  override def getEpochSecond: Long = timestamp
}

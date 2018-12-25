package jbok.core.consensus.pow.ethash

sealed trait PowMode
object PowMode {
  case object Normal   extends PowMode
  case object Shared   extends PowMode
  case object Test     extends PowMode
  case object Fake     extends PowMode
  case object FullFake extends PowMode
}

case class EthashConfig(
    cacheDir: String,
    cachesInMem: Int,
    cachesOnDisk: Int,
    datasetDir: String,
    datasetsInMem: Int,
    datasetsOnDisk: Int,
    powMode: PowMode
)

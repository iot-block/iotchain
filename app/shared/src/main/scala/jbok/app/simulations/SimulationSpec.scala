//package jbok.app.simulations
//
//import scala.util.Random
//
//class SimulationSpec extends FlatSpec with Matchers with PropertyChecks {
//  val db: KeyValueDB[IO]         = KeyValueDB.inMemory[IO].unsafeRunSync()
//  val world: WorldStateProxy[IO] = WorldStateProxy.inMemory[IO](db).unsafeRunSync()
//  "simulation fixture" should "valid transactions" in new BlockGenerator(world) {
//    setUp()
//    val stx: List[SignedTransaction] = generateValidSTX()
//  }
//
//  it should "one of transactions is invalid" in new BlockGenerator(world) {
//    setUp()
//    val stx: List[SignedTransaction] = generateValidSTX()
//    val index: Int                   = Random.nextInt(stx.size - 1)
//    stx.patch(index, Seq(stx(index).copy(nonce = stx(index).nonce - 1)), 1)
//  }
//
//  it should "double spend" in new BlockGenerator(world) {
//    setUp()
//    val stx = generateDoubleSpendSTX()
//  }
//}

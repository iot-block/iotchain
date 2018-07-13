package jbok.simulations

import cats.effect.IO
import fs2.StreamApp

object SimulationServer extends StreamApp[IO] {

  def apply(addr: NetAddress): IO[Server[IO]] =
    for {
      simulation <- Simulation[IO]
      service = JsonRPCService[IO].mountAPI[Simulation[IO]](simulation)
      server <- Server[IO](addr, service)
    } yield server

  override def stream(args: List[String], requestShutdown: IO[Unit]): fs2.Stream[IO, StreamApp.ExitCode] = {
    val addr = NetAddress("localhost", 9033)
    val server = apply(addr).unsafeRunSync()
    server.serve
  }
}

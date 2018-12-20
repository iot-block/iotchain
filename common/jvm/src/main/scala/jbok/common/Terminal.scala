package jbok.common

import cats.effect.Sync
import cats.implicits._

object Terminal {
  private val reader = new scala.tools.jline_embedded.console.ConsoleReader

  def putStr[F[_]: Sync](s: String): F[Unit] = Sync[F].delay {
    print(s)
    System.out.flush()
  }

  def putStrLn[F[_]: Sync](s: String): F[Unit] = Sync[F].delay {
    println(s)
  }

  def readLn[F[_]: Sync](prompt: String): F[String] = Sync[F].delay {
    reader.readLine(prompt)
  }

  def readPassword[F[_]: Sync](prompt: String): F[String] = Sync[F].delay {
    reader.readLine(prompt, Character.valueOf(0))
  }

  def choose[F[_]: Sync](prompt: String): F[Boolean] =
    readLn[F](s"${prompt} [Y/N]").map {
      case x if x.toLowerCase.startsWith("y") => true
      case _                                  => false
    }
}

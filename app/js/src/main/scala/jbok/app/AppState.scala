package jbok.app
import com.thoughtworks.binding.Binding.Var

case class AppState(
    config: Var[AppConfig],
    client: Var[Option[JbokClient]] = Var(None),
    number: Var[BigInt] = Var(0),
    gasPrice: Var[BigInt] = Var(0),
    gasLimit: Var[BigInt] = Var(0),
    miningStatus: Var[String] = Var("idle")
)

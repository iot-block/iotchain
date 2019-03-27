import sbt._
import sbt.Keys._
import wartremover.WartRemover.autoImport._

object WartRemoverPlugin {
  val settings = wartremoverErrors in (Compile, compile) := Warts.allBut(
    Wart.Any, // false positives
    Wart.ArrayEquals, // false positives
    Wart.Nothing, // false positives
    Wart.Null, // Java API under the hood; we have to deal with null
    Wart.Product, // false positives
    Wart.Serializable, // false positives
    Wart.ImplicitConversion, // we know what we're doing
    Wart.Throw, // TODO: switch to ApplicativeError.fail in most places
    Wart.PublicInference, // fails https://github.com/wartremover/wartremover/issues/398
    Wart.ImplicitParameter, // only used for Pos, but evidently can't be suppressed
    Wart.Equals,
    Wart.MutableDataStructures,
    Wart.DefaultArguments,
    Wart.Var,
    Wart.ToString,
    Wart.NonUnitStatements,
    Wart.Overloading,
    Wart.AsInstanceOf,
    Wart.Recursion,
    Wart.JavaConversions,
    Wart.Option2Iterable,
    Wart.TraversableOps // TODO
  )
}

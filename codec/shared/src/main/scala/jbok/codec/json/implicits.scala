package jbok.codec.json

import io.circe._
import io.circe.derivation.DerivationMacros
import io.circe.syntax._
import scodec.bits.ByteVector
import shapeless._
import shapeless.labelled._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.experimental.macros

@SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
object implicits {
  implicit val bytesDecoder: Decoder[ByteVector] = Decoder[String].emap(ByteVector.fromHexDescriptive(_))

  implicit val bytesEncoder: Encoder[ByteVector] = Encoder.instance(bv => Json.fromString(bv.toHex))

  implicit val bigIntDecoder: Decoder[BigInt] = Decoder[String].map[BigInt](
    x =>
      if (x.startsWith("0x"))
        BigInt(x.substring(2, x.length), 16)
      else
        BigInt(x))

  implicit val bigIntEncoder: Encoder[BigInt] = Encoder[String].contramap[BigInt](_.toString(10))

  implicit val durationEncoder: Encoder[Duration] = Encoder.instance[Duration](d => s"${d.length}${d.unit}".asJson)

  implicit val durationDecoder: Decoder[Duration] = Decoder[String].map(s => Duration.apply(s))

  implicit val finiteDurationEncoder: Encoder[FiniteDuration] =
    Encoder.instance[FiniteDuration](d => s"${d.length} ${d.unit.toString.toLowerCase}".asJson)

  implicit val finiteDurationDecoder: Decoder[FiniteDuration] =
    Decoder[String].map(s => Duration.apply(s).asInstanceOf[FiniteDuration])

  // key codecs
  implicit val bigIntKeyEncoder: KeyEncoder[BigInt] = KeyEncoder.encodeKeyString.contramap[BigInt](_.toString(10))

  implicit val bigIntKeyDecoder: KeyDecoder[BigInt] = KeyDecoder.decodeKeyString.map[BigInt](BigInt.apply)

  // codec for value classes
  implicit def decoderValueClass[T <: AnyVal, V](
      implicit
      g: Lazy[Generic.Aux[T, V :: HNil]],
      d: Decoder[V]
  ): Decoder[T] = Decoder.instance { cursor ⇒
    d(cursor).map { value ⇒
      g.value.from(value :: HNil)
    }
  }

  implicit def encoderValueClass[T <: AnyVal, V](
      implicit
      g: Lazy[Generic.Aux[T, V :: HNil]],
      e: Encoder[V]
  ): Encoder[T] = Encoder.instance { value ⇒
    e(g.value.to(value).head)
  }

  // codec for enum-like ADTs
  trait IsEnum[C <: Coproduct] {
    def to(c: C): String
    def from(s: String): Option[C]
  }

  object IsEnum {
    implicit val cnilIsEnum: IsEnum[CNil] = new IsEnum[CNil] {
      def to(c: CNil): String           = sys.error("Impossible")
      def from(s: String): Option[CNil] = None
    }
    implicit def cconsIsEnum[K <: Symbol, H <: Product, T <: Coproduct](implicit
                                                                        witK: Witness.Aux[K],
                                                                        witH: Witness.Aux[H],
                                                                        gen: Generic.Aux[H, HNil],
                                                                        tie: IsEnum[T]): IsEnum[FieldType[K, H] :+: T] =
      new IsEnum[FieldType[K, H] :+: T] {
        def to(c: FieldType[K, H] :+: T): String = c match {
          case Inl(h) => witK.value.name
          case Inr(t) => tie.to(t)
        }
        def from(s: String): Option[FieldType[K, H] :+: T] =
          if (s == witK.value.name) Some(Inl(field[K](witH.value)))
          else tie.from(s).map(Inr(_))
      }
  }

  implicit def encodeEnum[A, C <: Coproduct](implicit gen: LabelledGeneric.Aux[A, C], rie: IsEnum[C]): Encoder[A] =
    Encoder[String].contramap[A](a => rie.to(gen.to(a)))

  implicit def decodeEnum[A, C <: Coproduct](implicit gen: LabelledGeneric.Aux[A, C], rie: IsEnum[C]): Decoder[A] = Decoder[String].emap { s =>
    rie.from(s).map(gen.from).toRight("enum")
  }
}

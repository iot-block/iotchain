package jbok.codec.rlp

import magnolia._
import scodec._
import scodec.bits.BitVector

trait LowerPriorityRlpCodec {
  type Typeclass[A] = RlpCodec[A]

  def combine[A](ctx: CaseClass[RlpCodec, A]): RlpCodec[A] = {
    val codec = new Codec[A] {
      override def encode(value: A): Attempt[BitVector] =
        Attempt.successful {
          ctx.parameters.foldLeft(BitVector.empty)((acc, cur) => acc ++ cur.typeclass.encode(cur.dereference(value)).require)
        }

      override def decode(bits: BitVector): Attempt[DecodeResult[A]] = {
        val (fields, remainder) = ctx.parameters.foldLeft((List.empty[Any], bits)) {
          case ((acc, bits), cur) =>
            val v = cur.typeclass.decode(bits).require
            (v.value :: acc, v.remainder)
        }
        Attempt.successful(DecodeResult(ctx.rawConstruct(fields.reverse), remainder))
      }

      override def sizeBound: SizeBound =
        ctx.parameters match {
          case head :: _ =>
            ctx.parameters.foldLeft(head.typeclass.sizeBound)((acc, cur) => acc + cur.typeclass.sizeBound)
          case Nil =>
            SizeBound.unknown
        }

      override def toString: String =
        s"${ctx.typeName.short}(${ctx.parameters.map(p => s"${p.label}").mkString(",")})"
    }

    if (ctx.isValueClass) {
      RlpCodecHelper.fromCodec(PrefixType.NoPrefix, codec)
    } else {
      RlpCodecHelper.fromCodec(PrefixType.ListLenPrefix, codec)
    }
  }

  def dispatch[A](ctx: SealedTrait[RlpCodec, A]): RlpCodec[A] = {
    val m1 = ctx.subtypes.zipWithIndex.map(_.swap).toMap
    val m2 = ctx.subtypes.zipWithIndex.toMap
    val codec = new Codec[A] {
      override def encode(value: A): Attempt[BitVector] =
        ctx.dispatch(value)(sub => {
          val bits = codecs.uint8.encode(m2(sub)).require
          sub.typeclass.encode(sub.cast(value)).map(v => bits ++ v)
        })

      override def decode(bits: BitVector): Attempt[DecodeResult[A]] =
        codecs.uint8.decode(bits).flatMap { result =>
          m1.get(result.value) match {
            case Some(sub) => sub.typeclass.decode(result.remainder)
            case None      => Attempt.failure(Err(s"decode ADT failure, can not find its subtype instance of ${result.value}"))
          }
        }

      override def sizeBound: SizeBound = SizeBound.unknown
    }

    RlpCodecHelper.fromCodec(PrefixType.ItemLenPrefix, codec)
  }

  implicit def gen[A]: RlpCodec[A] = macro Magnolia.gen[A]
}

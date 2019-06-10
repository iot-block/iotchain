---
layout: docsplus
title:  "Codec"
number: 3
---

`Codec` module contains the encoding and decoding support for json & binary.

All codecs support JVM and ScalaJS.

## json

`circe` is used for all json codec, `codec/shared/src/main/scala/jbok/codec/json` contains some
custom instances.

For some primary types such as `BigInt`, we always delegate it to `String`, rather than `circe`'s default behavior.

Some instances for core models(e.g. `Address`, `Uint256`) are defined in their companion objects.

We prefer `@ConfiguredJsonCodec` annotation, so import `jbok.codec.json.implicits._` is a must.

```scala mdoc:silent
import jbok.codec.json.implicits._
import io.circe.syntax._
import io.circe.parser._
```

## binary

Typeclass `RlpCodec` is used for core models binary codec.

Some instances and ops are defined in `jbok/codec/rlp`.

```scala mdoc
import jbok.codec.rlp.RlpEncoded
import jbok.codec.rlp.implicits._

// encode A to RlpEncoded

"".encoded
0.encoded

case class Foo(name: String, age: Int)
Foo("oho", 18).encoded

List(Foo("cafe", 1), Foo("babe", 2)).encoded

// decode RlpEncoded to Either[Throwable, A]

val fooEncoded = Foo("bar", 42).encoded
fooEncoded.decoded[Foo]

// create `RlpEncoded` manually
RlpEncoded.coerce(fooEncoded.bits.drop(8)).decoded[Foo]
```

`rlp` is a low level, unoptimized binary codec protocol, but it's behavior is concise and predictable.

It's used to encode models like `Block` or `Transaction`, for computing stable message hashes.

`RlpCodec` is implemented based on `scodec` and `magnolia`(macros for generating instances for case classes)

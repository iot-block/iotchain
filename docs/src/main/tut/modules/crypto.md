---
layout: docsplus
title:  "Crypto"
number: 4
---

`Crypto` module is a basic crypto toolbox for cryptographic hash and digital signature.

CryptoJVM also add a simple helper for `SSL` on JVM.

## hash

```scala mdoc
import scodec.bits.ByteVector
import jbok.crypto._

val bytes = ByteVector("jbok".getBytes())
bytes.kec256.toHex
bytes.kec512.toHex
bytes.sha256.toHex
bytes.ripemd160.toHex
```

## signature

```scala mdoc
import scodec.bits.ByteVector
import jbok.crypto._
import jbok.crypto.signature._
import cats.effect.IO

val keyPair = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
val message = ByteVector("jbok".getBytes)
val chainId = BigInt(1)
val signature = Signature[ECDSA].sign[IO](message.kec256.toArray, keyPair, chainId).unsafeRunSync()

Signature[ECDSA].recoverPublic(message.kec256.toArray, signature, chainId)

// change chainId
Signature[ECDSA].recoverPublic(message.kec256.toArray, signature, chainId + 1)
```
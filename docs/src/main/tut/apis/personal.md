---
layout: docsplus
title:  "Personal"
number: 1
---

```scala
trait PersonalAPI[F[_]] {
  def importRawKey(privateKey: ByteVector, passphrase: String): F[Address]

  def newAccount(passphrase: String): F[Address]

  def delAccount(address: Address): F[Boolean]

  def listAccounts: F[List[Address]]

  def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): F[Boolean]

  def sendTransaction(
      from: Address,
      passphrase: String,
      to: Option[Address] = None,
      value: Option[BigInt] = None,
      gasLimit: Option[BigInt] = None,
      gasPrice: Option[BigInt] = None,
      nonce: Option[BigInt] = None,
      data: Option[ByteVector] = None,
  ): F[ByteVector]
}
```

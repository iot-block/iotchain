---
layout: docs
title:  "APIs"
position: 4
---

# APIs

You can call this api by http service.

For example, to call `BlockAPI.getBlockHeaderByNumber`, 

```bash
curl 47.102.121.72:30315/block/getBlockHeaderByNumber \
    -X POST \
    -H "Content-Type:application/json" \
    -d '{"number":"100"}'
```
Url path is `<host>:<port>/<apiPrefix>/<functionName>`, you can find `apiPrefix` and `functionName` in each api docs.

### [Account](./account.html)
- apiPrefix: `account`
- get an account(balance/storage/code) by its address at specific height(optional, default to latest)
- query accounts' history transactions

### [Admin](./admin.html)
- apiPrefix: `admin'
- mange peers manually
- consider turn it off or bind it to a local address

### [Block](./block.html)
- apiPrefix: `block'
- query blocks
- query chain state

### [Contract](./contract.html)
- apiPrefix: `contract'
- call a contract's method

### [Miner](./miner.html)
- apiPrefix: `miner'
- miner voting

### [Personal](./personal.html)
- apiPrefix: `personal'
- private keystore management
- consider turn it off or bind it to a local address if not necessary.

### [Transaction](./transaction.html)
- apiPrefix: `transaction'
- query transactions and receipts
- send signed transactions

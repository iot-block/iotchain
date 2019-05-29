---
layout: docsplus
title:  "Core"
number: 1
---

`Core` module contains the most important components, most of them are cross compiled against `ScalaJS`, except for components are relevant to `Socket`, `File`, etc.

- `api`: API traits definitions
- `config`: components configs
- `consensus`: Consensus engine
- `keystore`: basically a file-system backed store for encrypted private keys
- `ledger`: A `History` for keeping track the whole chain state, and a `BlockExecutor` for modifying it. 
- `messages`: Messages are gossiped by the peers.
- `miming`: A `BlockMiner` for `mine` new blocks.
- `modesl`: Defitions of `Block`, `Transaction`, `Receipt`, etc.
- `peer`: A `PeerManager` for managing both incoming and outgoing peers.
- `pool`: Some inemory structures to hold some intermediate objects between state transitions.
- `validators`: Validators for blocks and transactions.
- `evm`: Full evm support
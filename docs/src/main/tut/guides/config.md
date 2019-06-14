---
layout: docsplus
title:  "Config File"
number: 3
---

### gen sample config.yaml

```bash
$ bin/create-ca.sh
$ app/jvm/target/universal/stage/bin/network-builder-main
```
we can get 3 sample node config in `~/.jbok` directory, open `~/.jbok/node-0/config.yaml`.   

### rootPath
the node program will open this path, and create LOCK file to ensure only one process access this path.

```yaml
rootPath: /Users/playjokes/.jbok/node-0
```

### genesis
genesis part is the blockchain's most important part. It will generate first block in blockchain, and nodes have same genesis could connect each other.

chainId: the block id, we will sign everything with chainId to prevent replay attack. we recommand you choise a specific number that other blockchain never used.

alloc: here we init some accounts with default balance. 

miner: blockchain miner address, and only in this list can mine block.

timestamp: when the genesis block generated.

coinbase: first block coinbase.

difficulty: first block difficulty.

gasLimit: first block gasLimit.

```yaml
genesis:
  chainId: '10'
  alloc:
    '0x42405c55b353ff24e09b02374a0d8abdbf11d052': '1000000000000000000000000000000'
    '0xa490d92a1945744acb9700e5c3df74d7ffffd1ba': '1000000000000000000000000000000'
  miners:
    - '0xa490d92a1945744acb9700e5c3df74d7ffffd1ba'
  timestamp: 1558964460504
  coinbase: '0x0000000000000000000000000000000000000000'
  difficulty: '0'
  gasLimit: '16716680'
```

### log
logDir: logs dir.

level: set a value in [TRACE, DEBUG, INFO, WARN, ERROR] to get different level log.

maxLogs: save maxLogs days log in server.

```yaml
log:
  logDir: /var/log/jbok
  level: INFO
  maxLogs: 15
```

### history
we implement evm in node, set `frontierBlockNumber: '0'` will apply `frontierBlockNumber` config in evm. visit http://eips.ethereum.org/meta for more info.

```yaml
history:
    frontierBlockNumber: '0'
    homesteadBlockNumber: '0'
    tangerineWhistleBlockNumber: '0'
    spuriousDragonBlockNumber: '0'
    byzantiumBlockNumber: '0'
    constantinopleBlockNumber: '1000000000000000000000'
```

### keystore
initkey: you can init keystore with a file. 

dir: keystore dir.
```yaml
keystore:
  initkey: /etc/jbok/init.key
  dir: /var/lib/jbok/keystore
```

### peer

host: peer service host.

port: peer service port.

seeds: when node start, it will connect other node in this list. it must be given trusted other node.

updatePeersInterval: rotate peers interval.

maxOutgoingPeers: max outgoing peer connect.

maxIncomingPeers: max incoming peer connect.

minPeer: node will wait for peer if we connected peers less than minPeer

bufferSize: set client buffer size to send and read message.

timeout: if connected peer cannot receive and send to in timeout, we will close connection.

```yaml
peer:
  host: 0.0.0.0
  port: 30314
  seeds:
    - tcp://127.0.0.3:30314
    - tcp://127.0.0.4:30314
  updatePeersInterval: 10 seconds
  maxOutgoingPeers: 10
  maxIncomingPeers: 10
  minPeers: 0
  bufferSize: 4194304
  timeout: 10 seconds
```

### sync 
if a node we connected, and it's bestBlockNumber higher then us, we will sync to get blocks.

maxBlockHeadersPerRequest: max block header in one sync request 

maxBlockBodiesPerRequest: max block body in one sync request

offset: when we first connnect a better node, we will send sync request, max(1, our_best_block_number + 1 - offset), and get blocks compare with ours blocks.

checkInterval: sync service will awake every checkInterval time and check connected node's status.

banDuration: if we ban a node, it wi

requestTimeout: sync request timeout.

```yaml
sync:
  maxBlockHeadersPerRequest: 128
  maxBlockBodiesPerRequest: 128
  offset: 10
  checkInterval: 5 seconds
  banDuration: 200 seconds
  requestTimeout: 10 seconds
```

### txPool
node keep a txPool save txs in network and broadcast tx to other.

poolSize: max tx in pool.

transactionTimeout: if tx never be pack to block in transactionTimeout, expire it.

```yaml
txPool:
  poolSize: 4096
  transactionTimeout: 10 minutes
```

### blockPool
node keep a blockPool save block that cannot be attached behind history blocks. block number in range [bestBlcokNumber - maxBlockAheadA, bestBlockNumber + maxBlockBehind] will save into pool.

```yaml
blockPool:
  maxBlockAhead: 10
  maxBlockBehind: 10
```

### mining
miner node should set enabled be true.

address: miner address, and miner will mine block and sign block. so we should initkey with keystore at first. we can create keystore fold and paste miner key file or pass miner key file to keystore.initkey. 

passphrase: miner key file's passphrase

coinbase: pay reword to coinbase. if we mined a block, we will be paid reword for execution tx and pack blocks. 

period: mine block every period time.

epoch: set 30000 be a epoch, we reach epoch, votes in epoch will be cleared.

minBroadcastPeers: a block mined, at least broadcast to 4 peer.

```yaml
mining:
  enabled: true
  address: '0xa490d92a1945744acb9700e5c3df74d7ffffd1ba'
  passphrase: changeit
  coinbase: '0xc0e5f5502f906c55382090448b4a122fdf7db192'
  period: 5000 milliseconds
  epoch: 30000
  minBroadcastPeers: 4
```


### persist
blockchain's data to save.

driver: rocksdb. only support rocksdb now.

path: /path/to/data

columnFamilies: do not modify it.
```yaml
persist:
  driver: rocksdb
  path: /var/lib/jbok/data
  columnFamilies:
    - default
    - BlockHeader
    - BlockBody
    - Receipts
    - Snapshot
    - Node
    - Code
    - TotalDifficulty
    - AppState
    - Peer
    - NumberHash
    - TxLocation
```

### ssl
if you want use https protect you connection, you could set ssl enable.

```yaml
enabled: open or close ssl
keyStorePath: /path/to/service/cert
trustStorePath: /path/to/ca/cert
protocol: ssl protocol we used [SSL, SSLv2, SSLv3, TLS, TLSv1, TLSv1.1, TLSv1.2]
clientAuth: open or close clientAuth [NotRequested, Requested, Required]
```
```yaml
ssl:
  enabled: false
  keyStorePath: /etc/jbok/server.jks
  trustStorePath: /etc/jbok/cacert.jks
  protocol: TLS
  clientAuth: NotRequested
```
  
### db
service backend depends on database.

driver: database jdbc driver. support mysql, sqlite and h2: [com.mysql.cj.jdbc.MysqlDataSource, org.sqlite.JDBC, org.h2.Driver]

url: database uri.

user: user to login.

password: password for user.

```yaml
db:
  driver: com.mysql.cj.jdbc.MysqlDataSource
  url: jdbc:mysql://mysql:3306/jbok?useSSL=false
  user: root
  password: password
```
  
### service
enable: open or close service.

enableHttp2: http2 have hign performance than http/1. if we want enable http/2, we should set secure=true.

enableWebsockets: open or close websockets.

secure: if secure set be true, node will only open https service. And ssl config must set.

logHeader: open or close log header info.

logBody: open or close log body info.

enableMetrcs: open or close service metrics.

host: service interface.

port: service port.

apis: we can mount service in list [account, admin, block, contract, miner, personal, transaction] what we want. if you set 0.0.0.0 bind all interface, do not open admin, personal service.

```yaml
service:
  enable: true
  enableHttp2: 
  enableWebsockets: false
  secure: false
  logHeaders: true
  logBody: false
  enableMetrics: true
  host: 0.0.0.0
  port: 30315
  apis:
    - account
    - admin
    - block
    - contract
    - miner
    - personal
    - transaction
```

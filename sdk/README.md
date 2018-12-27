## JBOK SDK

### JavaScript Example

```js
// CommonJSModule
const jbok = require('./target/scala-2.12/scalajs-bundler/main/jbok-sdk-fastopt');

// modules
const client = jbok.Client;
const hasher = jbok.Hasher;
const jsonCodec = jbok.JsonCodec;
const binaryCodec = jbok.BinaryCodec;
const signer = jbok.Signer;


async function fun() {
  try {
    const uri = "ws://localhost:30316"; // rpc server endpoint
    const ws = await client.ws(uri);

    const params = {
      method: "getBlockByNumber",
      params: "1"
    };

    const json = JSON.stringify(params);
    const resp = await ws.jsonrpc(json); // standard jsonrpc protocol
    console.log("resp", resp);

    const result = JSON.parse(resp)['result'];
    console.log('result', result);
    const blockJson = JSON.stringify(result);

    const block = jsonCodec.decodeBlock(blockJson); // decode json into `Block`
    console.log('block', block);

    const bytes = binaryCodec.encodeBlockHeader(block.header); // encode block header into bytes(rlp)
    console.log('bytes', bytes);

    const hash = hasher.kec256(bytes); // calculate keccak256 hash
    console.log('hash', hash);
  } catch(err) {
    console.log('err', err);
  }
}

fun();
```


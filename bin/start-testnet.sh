#!/usr/bin/env bash

set -e

echo "clean test files"
rm -rf ~/.jbok/test-*

echo "build testnet"
app/jvm/target/universal/stage/bin/jbok-app build-testnet

for i in {0..3}
do
    echo "start node-${i}"
    nohup app/jvm/target/universal/stage/bin/jbok-app node ~/.jbok/test-${i}/app.json &>/dev/null &
done

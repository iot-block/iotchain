#!/usr/bin/env bash

set -e

echo "clean old testnet files"
rm -rf ~/.jbok/node-*

echo "build testnet"
app/jvm/target/universal/stage/bin/network-builder-main

for i in {0..2}
do
    echo "start node-${i}"
    nohup app/jvm/target/universal/stage/bin/app-main ~/.jbok/node-${i}/config.yaml &>/dev/null &
done

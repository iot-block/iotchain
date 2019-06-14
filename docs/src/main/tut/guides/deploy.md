---
layout: docsplus
title:  "Deploy Guide"
number: 2
---

### Getting Started

*before we start deploy, please follow develop doc first to install the dependency*

we have 2 way to deploy jbok node

### binary app-main

build the jbok node
```bash
$ bin/build.sh
```

in `app/jvm/target/universal/stage/bin`, we get 4 program
```
app-main: node
cli-main: a commod line connect to node
network-builder-main: build testnet config in ~/.jbok, follow config doc to build configs.
tx-generator-main:  gen random txs and send to testnet
```

`app/jvm/target/universal/stage/bin/app-main`
then start the node, given a node config path.
```bash
$ app/jvm/target/universal/stage/bin/app-main ~/.jbok/node-0/config.yaml
```

*install mysql follow: https://dev.mysql.com/doc/refman/8.0/en/linux-installation.html*

### docker

*install docker follow: https://docs.docker.com/compose/install/*

first we should build image and publish to local.

```bash
$ bin/build-docker.sh
``` 

the docker image based on openjdk:8-jre and expose ports 30314 and 30315.

Running node with docker-compose.
`docker-compose` will start prometheus, node-exporter, grafana, mysql,  mysqld-exporter, jbok services.
Jbok using data volume `./etc` and `./var`, for more info to config file `./docker-compose.yaml`.
```bash
$ docker-compose up
```

open http://localhost:3000/ to analytics and monitoring the services.

### jbok testnet

Create your own testnet. 

We will be using 3 node in testnet, 1 for miner, 2 for sync.

```bash
$ bin/build.sh
$ bin/create-ca.sh
$ bin/start-testnet.sh
```

the `~/.jbok`, will be root directory of nodes. every node's data will save into `node-#` directory.

modify `~/.jbok/node-$#/config.yaml` to change node config.

*WARN: do not modify genesis part*
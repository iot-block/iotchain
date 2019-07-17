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

### Deploy MainNet Node

*dependencies*

- Docker
- Docker-Compose

Before deploying, you need a keystore file.

- Generate keystore. 

You can create your account using our wallet app, then export the keystore.


There are 2 ways to deploy MainNet node: deploy with docker, deploy with docker-compose.

In this guide, I will deploy node in directory `/iotchain`.

####Deploy with Docker step-by-step
1. create directory for config datas and keystore file.

    ```bash
    $ cd /iotchain
    $ mkdir -p etc/iotchain
    $ mkdir etc/iotchain/keystore
    ```

2. upload file `config.yaml` to `/iotchain/etc/iotchain/config.yaml`
3. upload your keystore file to `/iotchain/etc/iotchain/keystore/<address>.json`.

    For example, if my address is `0eef064fd13402379b285d25f90af1beeb4f73eb`, it's filename will be `0eef064fd13402379b285d25f90af1beeb4f73eb.json`.
    
4. run docker.
    ```bash
    $ docker run -v $(pwd)/etc/iotchain:/etc/iotchain:ro 
        -v $(pwd)/var/lib/iotchain:/var/lib/iotchain 
        -v $(pwd)/var/log/iotchain:/var/log/iotchain 
        -p 30314:30314 
        -p 30315:30315 
        -d iotchain/iotchain:<version>
    ```
5. stop docker container.
    ```bash
    $ docker stop <CONTAINER_ID>
    ```
#### Deploy with Docker-Compose step-by-step
Docker-Compose will running prometheus for node metrics, and you can monitor this metrics in grafana. 

1. create directory for config datas and keystore file.

    ```bash
    $ cd /iotchain
    $ mkdir -p etc/iotchain
    $ mkdir etc/iotchain/keystore
    ```
2. upload file `docker-compose.yaml` to `/iotchain/docker-compose.yaml`.
3. upload file `config.yaml` to `/iotchain/etc/iotchain/config.yaml`
4. upload `etc/grafana`, `etc/mysql`, `etc/prometheus` to `/iotchain/etc/grafana`, `/iotchain/etc/mysql`, `/iotchain/etc/prometheus`.
5. run docker-compose.
    ```bash
    $ docker-compose up -d
    ```
    
    Open `http://yourip:3000`, monitoring node state.
6. stop docker-compose
    ```bash
    $ docker-compose stop
    ```
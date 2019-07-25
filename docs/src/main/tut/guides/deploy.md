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
```bash
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

*requires*
- **Keystore file**

    Keystore file is your node's identity, you can create your account using our Wallet App(https://iotchain.io/wallet), export the keystore from app.
    
    For example, if your address is `0x0eef064fd13402379b285d25f90af1beeb4f73eb`, your keystore filename will be `0eef064fd13402379b285d25f90af1beeb4f73eb.json`.

- **config.yaml**
    
    Make your own config.yaml by following steps:
    1. Get sample config.yaml in https://github.com/iot-block/iotchain/blob/master/etc/iotchain/config.yaml
    2. Make sure you have same genesis with our mainnet
    3. Change field 'service.host' in config.yaml
    4. Put your 'address' and 'coinbase' in relevant field
    5. Set 'miner.enabled = true' if you are a miner
- **Docker**
    
    ```bash
    $ yum install docker -y
    $ service docker start
    $ chkconfig docker on
    ```
- **[Optional] Docker-Compose**

    ```bash
    # If you deploy with docker-compose, you need install it.
  
    $ sudo curl -L "https://github.com/docker/compose/releases/download/1.24.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    $ sudo chmod +x /usr/local/bin/docker-compose
    ```

- **[Optional] docker-compose.yaml**
    
    If you deploy with docker-compose, you must have a docker-compose.yaml file,
  
    Get it from https://github.com/iot-block/iotchain/blob/master/docker-compose.yaml, 

- **[Optional] Grafana & Prometheus config files**
    
    You can get them in  https://github.com/iot-block/iotchain/tree/master/etc

There are 2 ways to deploy mainnet node: 

- deploy with docker
- deploy with docker-compose.

In this guide, I use directory `/iotchain` to deploy my node.

#### Deploy with Docker step-by-step
1. create directory for config datas and keystore file.

    ```bash
    $ cd /iotchain
    $ mkdir -p etc/iotchain/keystore
    ```

2. upload file `config.yaml` to `/iotchain/etc/iotchain/config.yaml`
3. upload your keystore file to `/iotchain/etc/iotchain/keystore/<address>.json`.
    ```bash
    #This is all files in directory /iotchain after this step.
    .
    ├── etc
    │   ├── iotchain
    │   │   ├── config.yaml
    │   │   └── keystore
    │   │       └── 0eef064fd13402379b285d25f90af1beeb4f73eb.json
    ```
    
4. run docker.
    ```bash
    $ docker run -v $(pwd)/etc/iotchain:/etc/iotchain:ro \
        -v $(pwd)/var/lib/iotchain:/var/lib/iotchain \
        -v $(pwd)/var/log/iotchain:/var/log/iotchain \
        -p 30314:30314 \
        -p 30315:30315 \
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
    $ mkdir -p etc/iotchain/keystore
    ```
2. upload file `docker-compose.yaml` to `/iotchain/docker-compose.yaml`.
3. upload file `config.yaml` to `/iotchain/etc/iotchain/config.yaml`
4. upload your keystore file to `/iotchain/etc/iotchain/keystore/<address>.json`.    
5. upload grafana & mysql & prometheus config files for metrics
    
    ```bash
    Config         |           Server Path
    -----------------------------------------------
    grafana        =>      /iotchain/etc/grafana 
    prometheus     =>      /iotchain/etc/prometheus 
 
    #This is all files in directory /iotchain after this step.
    .
    ├── docker-compose.yaml
    ├── etc
    │   ├── grafana
    │   │   ├── dashboards
    │   │   │   ├── alerts.json
    │   │   │   ├── iotchain.json
    │   │   │   ├── JVM dashboard.json
    │   │   │   └── node-exporter.json
    │   │   ├── grafana.ini
    │   │   └── provisioning
    │   │       ├── dashboards
    │   │       │   └── dashboard.yaml
    │   │       ├── datasources
    │   │       │   └── datasource.yaml
    │   │       └── notifiers
    │   │           └── notifiers.yaml
    │   ├── iotchain
    │   │   ├── config.yaml
    │   │   └── keystore
    │   │       └── 502a2d562e4ab07c111c833ecea0b29f034fdef9.json
    │   └── prometheus
    │       ├── alert.rules
    │       └── prometheus.yml
    ```
6. run docker-compose.

    ```bash
    $ docker-compose up -d
    ```
7. monitor

    ```
    1. open http://<yourIp>:3000,
    2. login with admin/admin,
    3. monitoring node state in grafana.
    ```

8. stop docker-compose

    ```bash
    $ docker-compose stop
    ```
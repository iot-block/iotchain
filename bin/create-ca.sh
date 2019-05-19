#!/usr/bin/env bash


rootDir=`pwd`/bin
generatedDir=${rootDir}/generated
caDir=${generatedDir}/ca
certsDir=${generatedDir}/certs

rm -rf ${generatedDir} && mkdir -p ${generatedDir}

mkdir -p ${caDir}/private
mkdir -p ${certsDir}/private

cd ${caDir}

# create ca private key and ca certificate
openssl ecparam -genkey -name secp521r1 -out ${caDir}/private/cakey.pem
openssl req -x509 -new -sha256 -key ${caDir}/private/cakey.pem -out ${caDir}/ca.pem -config ${rootDir}/ca-openssl.cnf -extensions v3_req -days 3650

cd ${certsDir}


read -p 'how many certs: ' nodeCount
for i in `seq 1 ${nodeCount}`; do
    read -p 'node ip: ' ip
    cp ${rootDir}/server-openssl.cnf server-openssl-${i}.cnf
    echo "IP.1 = ${ip}" >> server-openssl-${i}.cnf

    openssl ecparam -genkey -name secp521r1 -out ${certsDir}/private/certkey${i}.pem
    openssl req -new -sha256 -key ${certsDir}/private/certkey${i}.pem -out ${certsDir}/cert${i}.csr -config server-openssl-${i}.cnf
    openssl x509 -req -in cert${i}.csr -CA ${caDir}/ca.pem -CAkey ${caDir}/private/cakey.pem -CAcreateserial -out ${certsDir}/cert${i}.crt -days 356 -sha256
#    openssl x509 -new -sha256 -key ./private/cakey.pem -in ca.csr -out ca.pem -config ../../ca-openssl.cnf -extensions v3_req -days 3650
done


#!/usr/bin/env bash

rootDir=`pwd`/bin
generatedDir=${rootDir}/generated
caDir=${generatedDir}/ca
certsDir=${generatedDir}/certs

rm -rf ${generatedDir} && mkdir -p ${generatedDir}

mkdir -p ${caDir}/private
mkdir -p ${certsDir}/private

common_name_defualt="JBOK"
child_cert_cn_prefix="Node"

# create ca private key and ca certificate
cp ${rootDir}/ca-openssl.cnf ${caDir}/ca-openssl.cnf && cat ${rootDir}/distinguished_name.cnf >> ${caDir}/ca-openssl.cnf \
&& echo "CN=${common_name_defualt}" >> ${caDir}/ca-openssl.cnf

openssl ecparam -genkey -name secp521r1 -out ${caDir}/private/cakey.pem
openssl req -x509 -new -sha256 -key ${caDir}/private/cakey.pem -out ${caDir}/ca.pem -config ${caDir}/ca-openssl.cnf -extensions v3_req -days 3650 -outform PEM

# import to local ca/keystore.jks
echo "openssl export to pkcs12: "
openssl pkcs12 -export -out ${caDir}/certificate.pfx -inkey ${caDir}/private/caKey.pem -in ${caDir}/ca.pem
read -p 'keytool keystore password: ' -s password && echo
keytool -importkeystore -trustcacerts -deststorepass ${password} -destkeystore ${caDir}/keystore.jks -srckeystore ${caDir}/certificate.pfx -srcstoretype PKCS12

#ca_alias=IoT.Chain
#default_pass=changeit
#trustedCAStore=${JAVA_HOME}/jre/lib/security/cacerts
## delete/import/list  $JAVA_HOME/jre/lib/security/cacerts trusted certs
#sudo keytool -delete -alias ${ca_alias} -keystore ${trustedCAStore} -storepass "${default_pass}"
#sudo keytool -import -trustcacerts -file ${caDir}/ca.pem -alias ${ca_alias} -keystore ${trustedCAStore} -storepass "${default_pass}"
#keytool -list -v -alias ${ca_alias} -keystore ${trustedCAStore} -storepass "${default_pass}"


read -p 'how many certs: ' nodeCount
for i in `seq 1 ${nodeCount}`; do
    read -p 'node ip: ' ip
    conf_file=${certsDir}/server-openssl-${i}.cnf
    common_name=${child_cert_cn_prefix}${i}.${common_name_defualt}
    cp ${rootDir}/server-openssl.cnf ${conf_file} && echo "IP.1 = ${ip}" >> ${conf_file}
    cat ${rootDir}/distinguished_name.cnf >> ${conf_file} && echo "CN=${common_name}" >> ${conf_file}

    openssl ecparam -genkey -name secp521r1 -out ${certsDir}/private/certkey${i}.pem
    openssl req -new -sha256 -key ${certsDir}/private/certkey${i}.pem -out ${certsDir}/cert${i}.csr -config ${conf_file}
    openssl x509 -req -in ${certsDir}/cert${i}.csr -CA ${caDir}/ca.pem -CAkey ${caDir}/private/cakey.pem -CAcreateserial -out ${certsDir}/cert${i}.crt -days 356 -sha256 -outform PEM
done


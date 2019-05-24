#!/usr/bin/env bash

set -e

root_dir=`pwd`/bin
generated_dir=~/.jbok
ca_dir=${generated_dir}/ca
certs_dir=${generated_dir}/certs

rm -rf ${ca_dir} && mkdir -p ${ca_dir}

mkdir -p ${ca_dir}/private

common_name_defualt="JBOK"
ca_keystore_pass="changeit"

# create ca private key and ca certificate
cp ${root_dir}/ca-openssl.cnf ${ca_dir}/ca-openssl.cnf && cat ${root_dir}/distinguished_name.cnf >> ${ca_dir}/ca-openssl.cnf \
&& echo "CN=${common_name_defualt}" >> ${ca_dir}/ca-openssl.cnf

openssl ecparam -genkey -name secp521r1 -out ${ca_dir}/private/cakey.pem
openssl req -x509 -new -sha256 -key ${ca_dir}/private/cakey.pem -out ${ca_dir}/ca.pem -config ${ca_dir}/ca-openssl.cnf -extensions v3_req -days 3650 -outform PEM

# import to local ca/cacerts.jks
ca_alias=JBOK
keytool -import -trustcacerts -file ${ca_dir}/ca.pem -alias ${ca_alias} -storepass "${ca_keystore_pass}" -keystore ${ca_dir}/cacert.jks -noprompt

#!/usr/bin/env bash

set -e

server_keystore_pass="changeit"

echo "pwd" `pwd`

ip=$1
common_name=$2
tmp_conf_dir=$3
ca_dir=$4
cert_dir=$5

mkdir -p ${cert_dir}/private
conf_file=${cert_dir}/server-openssl.cnf
cp ${ca_dir}/cacert.jks ${cert_dir}/cacert.jks
cp ${tmp_conf_dir}/server-openssl.cnf ${conf_file} && echo "IP.1 = ${ip}" >> ${conf_file}
cat ${tmp_conf_dir}/distinguished_name.cnf >> ${conf_file} && echo "CN=${common_name}" >> ${conf_file}

# create server cert
openssl ecparam -genkey -name secp521r1 -out ${cert_dir}/private/certkey.pem
openssl req -new -sha256 -key ${cert_dir}/private/certkey.pem -out ${cert_dir}/cert.csr -config ${conf_file}
openssl x509 -req -in ${cert_dir}/cert.csr -CA ${ca_dir}/ca.pem -CAkey ${ca_dir}/private/cakey.pem -CAcreateserial -out ${cert_dir}/cert.pem -extfile ${conf_file} -extensions v3_req -days 3560 -sha256 -outform PEM

# export to server.jks
openssl pkcs12 -export -out ${cert_dir}/certificate.pfx -inkey ${cert_dir}/private/certKey.pem -in ${cert_dir}/cert.pem -password pass:"${server_keystore_pass}"
keytool -importkeystore -trustcacerts -storepass "${server_keystore_pass}" -destkeystore ${cert_dir}/server.jks -srckeystore ${cert_dir}/certificate.pfx -srcstoretype PKCS12 -deststoretype PKCS12 -srcstorepass "${server_keystore_pass}"

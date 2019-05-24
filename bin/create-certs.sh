#!/usr/bin/env bash

set -e

root_dir=`pwd`/bin
generated_dir=~/.jbok
ca_dir=${generated_dir}/ca
certs_dir=${generated_dir}/certs
server_keystore_pass="changeit"

read -p 'how many certs: ' count
for ((i = 0; i < ${count}; i++)); do
    read -p 'node IP: ' ip
    read -p 'node Common Name: ' common_name

    server_dir=${certs_dir}/cert-${common_name}
    rm -rf ${server_dir} && mkdir -p ${server_dir}/private
    conf_file=${server_dir}/server-openssl.cnf
#    common_name=${child_cert_cn_prefix}${i}.${common_name_defualt}
    cp ${root_dir}/server-openssl.cnf ${conf_file} && echo "IP.1 = ${ip}" >> ${conf_file}
    cat ${root_dir}/distinguished_name.cnf >> ${conf_file} && echo "CN=${common_name}" >> ${conf_file}

    # create server cert
    openssl ecparam -genkey -name secp521r1 -out ${server_dir}/private/certkey.pem
    openssl req -new -sha256 -key ${server_dir}/private/certkey.pem -out ${server_dir}/cert.csr -config ${conf_file}
    openssl x509 -req -in ${server_dir}/cert.csr -CA ${ca_dir}/ca.pem -CAkey ${ca_dir}/private/cakey.pem -CAcreateserial -out ${server_dir}/cert.pem -extfile ${conf_file} -extensions v3_req -days 3560 -sha256 -outform PEM

    # export to server.jks
    openssl pkcs12 -export -out ${server_dir}/certificate.pfx -inkey ${server_dir}/private/certKey.pem -in ${server_dir}/cert.pem -password pass:"${server_keystore_pass}"
    keytool -importkeystore -trustcacerts -storepass "${server_keystore_pass}" -destkeystore ${server_dir}/server.jks -srckeystore ${server_dir}/certificate.pfx -srcstoretype PKCS12 -deststoretype PKCS12 -srcstorepass "${server_keystore_pass}"
done
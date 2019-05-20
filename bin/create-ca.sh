#!/usr/bin/env bash

root_dir=`pwd`/bin
generated_dir=${root_dir}/generated
ca_dir=${generated_dir}/ca
certs_dir=${generated_dir}/certs

rm -rf ${generated_dir} && mkdir -p ${generated_dir}

mkdir -p ${ca_dir}/private

common_name_defualt="JBOK"
child_cert_cn_prefix="Node"
ca_keystore_pass="changeit"
server_keystore_pass="changeit"

# create ca private key and ca certificate
cp ${root_dir}/ca-openssl.cnf ${ca_dir}/ca-openssl.cnf && cat ${root_dir}/distinguished_name.cnf >> ${ca_dir}/ca-openssl.cnf \
&& echo "CN=${common_name_defualt}" >> ${ca_dir}/ca-openssl.cnf

openssl ecparam -genkey -name secp521r1 -out ${ca_dir}/private/cakey.pem
openssl req -x509 -new -sha256 -key ${ca_dir}/private/cakey.pem -out ${ca_dir}/ca.pem -config ${ca_dir}/ca-openssl.cnf -extensions v3_req -days 3650 -outform PEM

# import to local ca/cacerts.jks
ca_alias=JBOK
keytool -import -trustcacerts -file ${ca_dir}/ca.pem -alias ${ca_alias} -storepass "${ca_keystore_pass}" -keystore ${ca_dir}/cacert.jks -noprompt

#default_pass=changeit
#trustedCAStore=${JAVA_HOME}/jre/lib/security/cacerts
## delete/import/list  $JAVA_HOME/jre/lib/security/cacerts trusted certs
#sudo keytool -delete -alias ${ca_alias} -keystore ${trustedCAStore} -storepass "${default_pass}"
#sudo keytool -import -trustcacerts -file ${caDir}/ca.pem -alias ${ca_alias} -keystore ${trustedCAStore} -storepass "${default_pass}"
#keytool -list -v -alias ${ca_alias} -keystore ${trustedCAStore} -storepass "${default_pass}"


read -p 'how many certs: ' nodeCount
for i in `seq 1 ${nodeCount}`; do
    server_dir=${certs_dir}/certs${i}
    mkdir -p ${server_dir}/private
    conf_file=${server_dir}/server-openssl.cnf
    common_name=${child_cert_cn_prefix}${i}.${common_name_defualt}
    read -p 'node ip: ' ip
    cp ${root_dir}/server-openssl.cnf ${conf_file} && echo "IP.1 = ${ip}" >> ${conf_file}
    cat ${root_dir}/distinguished_name.cnf >> ${conf_file} && echo "CN=${common_name}" >> ${conf_file}

    # create server cert
    openssl ecparam -genkey -name secp521r1 -out ${server_dir}/private/certkey.pem
    openssl req -new -sha256 -key ${server_dir}/private/certkey.pem -out ${server_dir}/cert.csr -config ${conf_file}
    openssl x509 -req -in ${server_dir}/cert.csr -CA ${ca_dir}/ca.pem -CAkey ${ca_dir}/private/cakey.pem -CAcreateserial -out ${server_dir}/cert.pem -days 356 -sha256 -outform PEM

    # export to server.jks
    openssl pkcs12 -export -out ${server_dir}/certificate.pfx -inkey ${server_dir}/private/certKey.pem -in ${server_dir}/cert.pem -password pass:"${server_keystore_pass}"
    keytool -importkeystore -trustcacerts -storepass "${server_keystore_pass}" -destkeystore ${server_dir}/server.jks -srckeystore ${server_dir}/certificate.pfx -srcstoretype PKCS12 -srcstorepass "${server_keystore_pass}"
done


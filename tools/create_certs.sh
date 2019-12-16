openssl req -new -x509 -sha256 -newkey rsa:2048 -keyout tls.key -out tls.crt -days 3650 -nodes -subj '/CN=localhost'
openssl pkcs12 -export -in tls.crt -inkey tls.key -out tls.p12
keytool -importkeystore -srckeystore tls.p12 -srcstoretype PKCS12 -destkeystore tls.jks -deststoretype JKS

#!/bin/zsh
: "${CHART_NAME=platform}"
: "${SERVICE=secure-service}"
: "${SERVICE_NAME=${SERVICE}-${CHART_NAME}}"
: "${NAMESPACE=default}"
: "${KEYSTORE_PASSWORD=key-changeme}"
: "${PRIVATE_KEY_PASSWORD=key-changeme}"
: "${TRUSTSTORE_PASSWORD=trust-changeme}"

function generate_certificates() {
  cd "$1" || exit 2
  openssl genrsa -out ca.key 2048 > /dev/null

  openssl req -new -x509 -sha256 -days 3650 -key ca.key \
    -subj "/C=CA/CN=$SERVICE_NAME.$NAMESPACE.svc" \
    -reqexts SAN \
    -extensions SAN \
    -config <(echo "[req]"; echo "distinguished_name=req"; echo "[SAN]"; echo "subjectAltName=DNS:localhost") \
    -out ca.crt >/dev/null

  openssl req -newkey rsa:2048 -nodes -sha256 -keyout server.key \
    -subj "/C=CA/CN=$SERVICE_NAME.$NAMESPACE.svc" \
    -reqexts SAN \
    -extensions SAN \
    -config <(echo "[req]"; echo "distinguished_name=req"; echo "[SAN]"; echo "subjectAltName=DNS:localhost") \
    -out server.csr >/dev/null

  openssl x509 -req \
    -extfile <(printf "subjectAltName=DNS:%s.%s.svc,DNS:localhost" "${SERVICE_NAME}" "${NAMESPACE}") \
    -sha256 \
    -days 3650 \
    -in server.csr \
    -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out server.crt >/dev/null

  echo ">> Generating keystore and truststore files"

  openssl pkcs12 -export -inkey server.key -in server.crt -out key.pkcs12 -password pass:"$KEYSTORE_PASSWORD" >/dev/null
  keytool -importkeystore -noprompt -srckeystore key.pkcs12 -srcstoretype pkcs12 -deststoretype JKS -destkeystore keystore.jks -destkeypass "$PRIVATE_KEY_PASSWORD" -storepass "$KEYSTORE_PASSWORD" -srcstorepass "$KEYSTORE_PASSWORD" >/dev/null

  keytool -exportcert \
            --alias 1 \
            -keystore key.pkcs12 \
            -storepass "$KEYSTORE_PASSWORD" \
            -rfc -file "${SERVICE_NAME}".pem > /dev/null

  keytool -import -noprompt -keystore truststore.jks -file server.crt -storepass "$TRUSTSTORE_PASSWORD" -alias "service-server" >/dev/null
  keytool -import -noprompt -keystore truststore.jks -file ca.crt -storepass "$TRUSTSTORE_PASSWORD" -alias "ca-server" >/dev/null

  kubectl create secret generic "${SERVICE_NAME}"-keystore-${CHART_NAME} \
    --from-file=keystore.jks=keystore.jks \
    --dry-run=client -o yaml \
    > "${SERVICE_NAME}"-keystore-secret.yaml

  kubectl create secret generic "${SERVICE_NAME}"-keystore-password-${CHART_NAME} \
      --from-literal=keystore.password="$KEYSTORE_PASSWORD" \
      --dry-run=client -o yaml \
      > "${SERVICE_NAME}"-keystore-password-secret.yaml

  kubectl create secret generic "${SERVICE_NAME}"-truststore-${CHART_NAME} \
      --from-file=truststore.jks=truststore.jks \
      --dry-run=client -o yaml \
      > "${SERVICE_NAME}"-truststore-secret.yaml

  kubectl create secret generic "${SERVICE_NAME}"-truststore-password-${CHART_NAME} \
      --from-literal=truststore.password="$TRUSTSTORE_PASSWORD" \
      --dry-run=client -o yaml \
      > "${SERVICE_NAME}"-truststore-password-secret.yaml

  echo ">> Create client certificates"

  openssl req -x509 -newkey rsa:2048 -keyout "${SERVICE_NAME}"-client-key.pem -out "${SERVICE_NAME}"-client-cert.pem -days 360 -subj "/CN=client" -nodes
  openssl x509 -outform der -in "${SERVICE_NAME}"-client-cert.pem -out "${SERVICE_NAME}"-client-cert.crt

  echo ">> Import the certificates"
  keytool -import -file "${SERVICE_NAME}"-client-cert.crt -alias "${SERVICE_NAME}"-client -keystore truststore.jks -storepass $TRUSTSTORE_PASSWORD -trustcacerts -noprompt
}

generate_certificates "$@"

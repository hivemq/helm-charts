#!/bin/bash

# Configuration
KEYSTORE_FILE="keystore.jks"
KEY_ALIAS="myalias"
KEYSTORE_PASSWORD="changeit"
KEY_PASSWORD="changeit"
DNAME="CN=example.com, OU=IT, O=MyCompany, L=City, ST=State, C=US"
VALIDITY_DAYS=365

# Remove existing keystore if it exists
if [ -f "$KEYSTORE_FILE" ]; then
    echo "Removing existing keystore: $KEYSTORE_FILE"
    rm "$KEYSTORE_FILE"
fi

# Generate a new keystore with a self-signed certificate
keytool -genkeypair \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY_DAYS" \
    -keystore "$KEYSTORE_FILE" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "$DNAME"

# Verify the keystore
keytool -list -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD"

echo "Keystore '$KEYSTORE_FILE' created successfully with alias '$KEY_ALIAS'."

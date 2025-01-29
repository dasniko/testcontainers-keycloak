FROM quay.io/keycloak/keycloak:nightly AS builder

# copied from: https://www.keycloak.org/server/containers
# DISCLAIMER THIS IS ONLY FOR TESTING PURPOSE. DO NOT, I REAPEAT DO NOT USE THIS Dockerfile IN PRODUCTION

ENV KC_HEALTH_ENABLED=true

WORKDIR /opt/keycloak
# for demonstration purposes only, please make sure to use proper certificates in production instead
RUN keytool -genkeypair -storepass password -storetype PKCS12 -keyalg RSA -keysize 2048 -dname "CN=server" -alias server -ext "SAN:c=DNS:localhost,IP:127.0.0.1" -keystore conf/server.keystore
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:nightly
COPY --from=builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]

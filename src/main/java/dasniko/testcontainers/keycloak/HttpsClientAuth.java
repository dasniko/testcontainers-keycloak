package dasniko.testcontainers.keycloak;

public enum HttpsClientAuth {

    NONE,
    REQUEST,
    REQUIRED;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}

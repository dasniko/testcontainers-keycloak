package dasniko.testcontainers.keycloak;

import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainerHttpsTest {

    @BeforeEach
    public void setup() {
        RestAssured.reset();
    }

    @Test
    public void shouldStartKeycloakWithTlsSupport() {
        try (KeycloakContainer keycloak = new KeycloakContainer().useTls()) {
            keycloak.start();

            RestAssured.useRelaxedHTTPSValidation();

            assertThat(keycloak.getAuthServerUrl(), startsWith("https://"));

            given()
                .when().get(keycloak.getAuthServerUrl())
                .then().statusCode(200);
        }
    }

    @Test
    public void shouldStartKeycloakWithProvidedTlsKeystore() {
        try (KeycloakContainer keycloak = new KeycloakContainer().useTls()) {
            keycloak.start();
            checkTls(keycloak, "tls.jks", "changeit");
        }
    }

    @Test
    public void shouldStartKeycloakWithCustomTlsCertAndKey() {
        try (KeycloakContainer keycloak = new KeycloakContainer().useTls("keycloak.crt", "keycloak.key")) {
            keycloak.start();
            checkTls(keycloak, "keycloak.jks", "keycloak");
        }
    }

    @Test
    public void shouldStartKeycloakWithCustomTlsKeystore() {
        try (KeycloakContainer keycloak = new KeycloakContainer().useTlsKeystore("keycloak.jks", "keycloak")) {
            keycloak.start();
            checkTls(keycloak, "keycloak.jks", "keycloak");
        }
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionUponEmptyTlsCertificateKeyFilename() {
        assertThrows(IllegalArgumentException.class, () -> new KeycloakContainer().useTls("keycloak.crt", ""));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionUponEmptyTlsKeystore() {
        assertThrows(IllegalArgumentException.class, () -> new KeycloakContainer().useTlsKeystore("keycloak.jks", ""));
    }

    private void checkTls(KeycloakContainer keycloak, String pathToTruststore, String truststorePassword) {
        RestAssured.config = RestAssured.config().sslConfig(
            SSLConfig.sslConfig().trustStore(pathToTruststore, truststorePassword)
        );

        assertThat(keycloak.getAuthServerUrl(), startsWith("https://"));

        given()
            .when().get(keycloak.getAuthServerUrl())
            .then().statusCode(200);
    }

}

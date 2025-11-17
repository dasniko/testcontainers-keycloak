package dasniko.testcontainers.keycloak;

import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class KeycloakContainerHttpsLegacyTest {

    @BeforeEach
    public void setup() {
        RestAssured.reset();
    }

    @Test
    public void shouldStartKeycloakWithMutualTlsRequestNoMutualTls() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .useTlsKeystore("keycloak.jks", "keycloak")
            .useMutualTls("keycloak.jks", "keycloak", HttpsClientAuth.REQUEST)) {
            keycloak.start();
            checkTls(keycloak, "keycloak.jks", "keycloak");
        }
    }

    @Test
    public void shouldStartKeycloakWithMutualTlsRequestWithMutualTls() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .useTlsKeystore("keycloak.jks", "keycloak")
            .useMutualTls("keycloak.jks", "keycloak", HttpsClientAuth.REQUEST)) {
            keycloak.start();
            checkMutualTls(keycloak, "keycloak.jks", "keycloak", "keycloak.jks", "keycloak");
        }
    }

    @Test
    @Disabled("temporarily disabled, until cause and fix is clarified")
    public void shouldStartKeycloakWithMutualTlsRequiredWithMutualTls() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .useTlsKeystore("keycloak.jks", "keycloak")
            .useMutualTls("keycloak.jks", "keycloak", HttpsClientAuth.REQUIRED)
        ) {
            keycloak.start();
            checkMutualTls(keycloak, "keycloak.jks", "keycloak", "keycloak.jks", "keycloak");
        }
    }

    @Test
    public void shouldStartKeycloakWithMutualTlsRequiredWithoutMutualTls() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .useTlsKeystore("keycloak.jks", "keycloak")
            .useMutualTls("keycloak.jks", "keycloak", HttpsClientAuth.REQUIRED)
        ) {
            keycloak.start();
            assertThrows(SSLHandshakeException.class, () -> checkTls(keycloak, "keycloak.jks", "keycloak"));
        }
    }

    @Test
    public void shouldThrowNullPointerExceptionUponNullTlsTruststoreFilename() {
        assertThrows(NullPointerException.class, () -> new KeycloakContainer().useMutualTls(null, null, HttpsClientAuth.NONE));
    }

    @Test
    public void shouldThrowNullPointerExceptionUponNullHttpsClientAuth() {
        assertThrows(NullPointerException.class, () -> new KeycloakContainer().useMutualTls("keycloak.jks", null, null));
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

    private void checkMutualTls(KeycloakContainer keycloak, String pathToTruststore, String truststorePassword, String pathToKeystore,
        String keystorePassword) {
        RestAssured.config = RestAssured.config().sslConfig(
            SSLConfig.sslConfig()
                .trustStore(pathToTruststore, truststorePassword)
                .keyStore(pathToKeystore, keystorePassword)
        );

        assertThat(keycloak.getAuthServerUrl(), startsWith("https://"));

        given()
            .when().get(keycloak.getAuthServerUrl())
            .then().statusCode(200);
    }

}

package dasniko.testcontainers.keycloak;

import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ServerInfoResource;

import javax.net.ssl.SSLHandshakeException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @Disabled
    public void shouldStartKeycloakWithMutualTlsRequiredWithMutualTls() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .useTlsKeystore("keycloak.jks", "keycloak")
            .useMutualTls("keycloak.jks", "keycloak", HttpsClientAuth.REQUIRED)) {
            keycloak.start();
            checkMutualTls(keycloak, "keycloak.jks", "keycloak", "keycloak.jks", "keycloak");
        }
    }

    @Test
    @Disabled
    public void shouldStartKeycloakWithMutualTlsRequiredWithoutMutualTls() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .useTlsKeystore("keycloak.jks", "keycloak")
            .useMutualTls("keycloak.jks", "keycloak", HttpsClientAuth.REQUIRED)) {
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

    @Test
    public void shouldThrowNullPointerExceptionUponNullTlsCertificateKeyFilename() {
        assertThrows(NullPointerException.class, () -> new KeycloakContainer().useTls("keycloak.crt", null));
    }

    @Test
    public void shouldThrowNullPointerExceptionUponNullTlsKeystore() {
        assertThrows(NullPointerException.class, () -> new KeycloakContainer().useTlsKeystore("keycloak.jks", null));
    }

    @Test
    public void shouldAdminClientBeAbleToConnectWithProvidedTlsKeystore() {
        try (KeycloakContainer keycloak = new KeycloakContainer().useTls()) {
            keycloak.start();
            checkAdminClient(keycloak);
        }
    }

    @Test
    public void shouldAdminClientBeAbleToConnectWithCustomTlsCertAndKey() {
        try (KeycloakContainer keycloak = new KeycloakContainer().useTls("keycloak.crt", "keycloak.key")) {
            keycloak.start();
            checkAdminClient(keycloak);
        }
    }

    @Test
    public void shouldAdminClientBeAbleToConnectWithCustomTlsKeystore() {
        try (KeycloakContainer keycloak = new KeycloakContainer().useTlsKeystore("keycloak.jks", "keycloak")) {
            keycloak.start();
            checkAdminClient(keycloak);
        }
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

    private void checkAdminClient(KeycloakContainer keycloak) {
        Keycloak admin = keycloak.getKeycloakAdminClient();
        ServerInfoResource serverInfoResource = admin.serverInfo();
        assertNotNull(serverInfoResource.getInfo());
    }

}

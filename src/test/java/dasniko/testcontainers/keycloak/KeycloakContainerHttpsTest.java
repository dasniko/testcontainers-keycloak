package dasniko.testcontainers.keycloak;

import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import org.junit.Before;
import org.junit.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainerHttpsTest {

    @Before
    public void setup() {
        RestAssured.reset();
    }

    @Test
    public void shouldStartKeycloakWithDefaultTlsSupport() {
        try (KeycloakContainer keycloak = new KeycloakContainer()) {
            keycloak.start();

            RestAssured.useRelaxedHTTPSValidation();

            given()
                .when().get("https://localhost:" + keycloak.getHttpsPort() + "/auth")
                .then().statusCode(200);
        }
    }

    @Test
    public void shouldStartKeycloakWithProvidedTlsCertAndKey() {
        try (KeycloakContainer keycloak = new KeycloakContainer().useTls()) {
            keycloak.start();
            checkTls(keycloak, "tls.jks", "changeit");
        }
    }

    @Test
    public void shouldStartKeycloakWithCustomTlsCertAndKey() {
        try (KeycloakContainer keycloak = new KeycloakContainer().useTls("keycloak.crt", "keycloak.key")) {
            keycloak.start();
            checkTls(keycloak,"keycloak.jks", "keycloak");
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

}

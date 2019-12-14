package dasniko.testcontainers.keycloak;

import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import org.junit.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainerHttpsTest {

    @Test
    public void shouldStartKeycloakWithHttpsSupport() {
        try (KeycloakContainer keycloak = new KeycloakContainer().withHttps(true).withRealmImportFile("test-realm.json")) {
            keycloak.start();

            RestAssured.config = RestAssured.config().sslConfig(
                SSLConfig.sslConfig().trustStore("keycloak.jks", "keycloak")
            );

            String accountService = given()
                .when().get(keycloak.getAuthServerUrl() + "/realms/test")
                .then().statusCode(200).body("realm", equalTo("test"))
                .extract().path("account-service");

            given().when().get(accountService).then().statusCode(200);
        }
    }

}

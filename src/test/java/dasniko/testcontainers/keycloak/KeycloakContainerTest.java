package dasniko.testcontainers.keycloak;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
@Slf4j
public class KeycloakContainerTest {

    @Test
    public void shouldStartKeycloak() {
        try (KeycloakContainer keycloak = new KeycloakContainer()) {
            keycloak.start();
        }
    }

    @Test
    public void shouldImportRealm() {
        try (KeycloakContainer keycloak = new KeycloakContainer().withRealmImportFile("test-realm.json")) {
            keycloak.start();

            String accountService = given().when().get(keycloak.getAuthServerUrl() + "/realms/test")
                .then().statusCode(200).body("realm", equalTo("test"))
                .extract().path("account-service");

            given().when().get(accountService).then().statusCode(200);
        }
    }
}

package dasniko.testcontainers.keycloak;

import org.junit.Test;
import org.keycloak.representations.info.ServerInfoRepresentation;

import static dasniko.testcontainers.keycloak.KeycloakContainer.KEYCLOAK_VERSION;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
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

    @Test
    public void shouldReturnServerInfo() {
        try (KeycloakContainer keycloak = new KeycloakContainer()) {
            keycloak.start();

            ServerInfoRepresentation serverInfo = keycloak.getKeycloakAdminClient().serverInfo().getInfo();
            assertThat(serverInfo, notNullValue());
            assertThat(serverInfo.getSystemInfo().getVersion(), equalTo(KEYCLOAK_VERSION));
        }
    }

}

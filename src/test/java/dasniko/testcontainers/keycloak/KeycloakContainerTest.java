package dasniko.testcontainers.keycloak;

import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.info.ServerInfoRepresentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainerTest {

    public static final String MASTER = "master";
    public static final String ADMIN_CLI = "admin-cli";

    public static final String TEST_REALM_JSON = "test-realm.json";

    @Test
    public void shouldStartKeycloak() {
        try (KeycloakContainer keycloak = new KeycloakContainer()) {
            keycloak.start();
        }
    }

    @Test
    public void shouldImportRealm() {
        try (KeycloakContainer keycloak = new KeycloakContainer().withRealmImportFile(TEST_REALM_JSON)) {
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

            checkKeycloakContainerInternals(keycloak, keycloak.getAdminUsername(), keycloak.getAdminPassword());
        }
    }

    @Test
    public void shouldUseDifferentAdminCredentials() {
        String username = "foo";
        String password = "bar";

        try (KeycloakContainer keycloak = new KeycloakContainer()
            .withAdminUsername(username)
            .withAdminPassword(password)) {
            keycloak.start();

            checkKeycloakContainerInternals(keycloak, username, password);
        }
    }

    private void checkKeycloakContainerInternals(KeycloakContainer keycloak, String username, String password) {
        Keycloak keycloakAdminClient = KeycloakBuilder.builder()
            .serverUrl(keycloak.getAuthServerUrl())
            .realm(MASTER)
            .clientId(ADMIN_CLI)
            .username(username)
            .password(password)
            .build();

        ServerInfoRepresentation serverInfo = keycloakAdminClient.serverInfo().getInfo();
        assertThat(serverInfo, notNullValue());
        assertThat(serverInfo.getSystemInfo().getVersion(), equalTo(keycloak.getKeycloakVersion()));
    }

}

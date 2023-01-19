package dasniko.testcontainers.keycloak;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.info.ServerInfoRepresentation;
import org.testcontainers.containers.ContainerLaunchException;

import java.time.Duration;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainerTest {

    public static final String TEST_REALM_JSON = "/test-realm.json";

    @Test
    public void shouldStartKeycloak() {
        try (KeycloakContainer<?> keycloak = new KeycloakContainer<>()) {
            keycloak.start();
        }
    }

    @Test
    public void shouldConsiderConfiguredStartupTimeout() {
        final int MAX_TIMEOUT = 5;
        Instant start = Instant.now();
        try {
            Duration duration = Duration.ofSeconds(MAX_TIMEOUT);
            try (KeycloakContainer<?> keycloak = new KeycloakContainer<>().withStartupTimeout(duration)) {
                keycloak.start();
            }
        } catch(ContainerLaunchException ex) {
            Duration observedDuration = Duration.between(start, Instant.now());
            assertTrue(observedDuration.toSeconds() >= MAX_TIMEOUT && observedDuration.toSeconds() < 30,
                String.format("Startup time should consider configured limit of %d seconds, but took %d seconds",
                    MAX_TIMEOUT, observedDuration.toSeconds()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {TEST_REALM_JSON, "/single-test-folder" + TEST_REALM_JSON, "/second-test-folder/first-test-folder" + TEST_REALM_JSON})
    public void shouldImportRealm(final String realmLocation) {
        try (KeycloakContainer<?> keycloak = new KeycloakContainer<>().withRealmImportFile(realmLocation)) {
            keycloak.start();

            String accountService = given().when().get(keycloak.getAuthServerUrl() + "realms/test")
                .then().statusCode(200).body("realm", equalTo("test"))
                .extract().path("account-service");

            given().when().get(accountService).then().statusCode(200);
        }
    }

    @Test
    public void shouldImportMultipleRealms() {
        try (KeycloakContainer<?> keycloak = new KeycloakContainer<>().
            withRealmImportFiles(TEST_REALM_JSON, "/another-realm.json")) {
            keycloak.start();

            String accountService = given().when().get(keycloak.getAuthServerUrl() + "realms/test")
                .then().statusCode(200).body("realm", equalTo("test"))
                .extract().path("account-service");

            given().when().get(accountService).then().statusCode(200);

            accountService = given().when().get(keycloak.getAuthServerUrl() + "realms/another")
                .then().statusCode(200).body("realm", equalTo("another"))
                .extract().path("account-service");

            given().when().get(accountService).then().statusCode(200);
        }
    }

    @Test
    public void shouldReturnServerInfo() {
        try (KeycloakContainer<?> keycloak = new KeycloakContainer<>()) {
            keycloak.start();

            checkKeycloakContainerInternals(keycloak);
        }
    }

    @Test
    public void shouldUseDifferentAdminCredentials() {
        try (KeycloakContainer<?> keycloak = new KeycloakContainer<>()
            .withAdminUsername("foo")
            .withAdminPassword("bar")) {
            keycloak.start();

            checkKeycloakContainerInternals(keycloak);
        }
    }

    @Test
    public void shouldRunOnDifferentContextPath() {
        String contextPath = "/auth/";
        try (KeycloakContainer<?> keycloak = new KeycloakContainer<>().withContextPath(contextPath)) {
            keycloak.start();

            String authServerUrl = keycloak.getAuthServerUrl();
            assertThat(authServerUrl, endsWith(contextPath));

            given().when().get(authServerUrl + "realms/master/.well-known/openid-configuration")
                .then().statusCode(200);

            checkKeycloakContainerInternals(keycloak);
        }
    }

    @Test
    public void shouldCacheStaticContentPerDefault() {
        try (KeycloakContainer<?> keycloak = new KeycloakContainer<>()) {
            keycloak.start();

            String authServerUrl = keycloak.getAuthServerUrl();
            given().when().get(getProjectLogoUrl(authServerUrl))
                .then().statusCode(200).header("Cache-Control", containsString("max-age=2592000"));
        }
    }

    @Test
    public void shouldNotCacheStaticContentWithDisabledCaching() {
        try (KeycloakContainer<?> keycloak = new KeycloakContainer<>().withDisabledCaching()) {
            keycloak.start();

            String authServerUrl = keycloak.getAuthServerUrl();
            given().when().get(getProjectLogoUrl(authServerUrl))
                .then().statusCode(200).header("Cache-Control", "no-cache");
        }
    }

    private void checkKeycloakContainerInternals(KeycloakContainer<?> keycloak) {
        Keycloak keycloakAdminClient = keycloak.getKeycloakAdminClient();
        ServerInfoRepresentation serverInfo = keycloakAdminClient.serverInfo().getInfo();
        assertThat(serverInfo, notNullValue());
        assertThat(serverInfo.getSystemInfo().getVersion(), equalTo(keycloak.getKeycloakVersion()));
    }

    private String getProjectLogoUrl(String authServerUrl) {
        return authServerUrl + "welcome-content/keycloak-project.png";
    }

}

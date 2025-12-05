package dasniko.testcontainers.keycloak;

import io.restassured.response.ValidatableResponse;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.info.ServerInfoRepresentation;
import org.testcontainers.containers.ContainerLaunchException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainerTest {

    public static final String TEST_REALM_JSON = "/test-realm.json";
    public static final String MASTER_REALM_WITH_ADMIN_USER_JSON = "/master-realm-with-admin-user.json";

    @Test
    public void shouldStartKeycloak() {
        try (KeycloakContainer keycloak = new KeycloakContainer()) {
            keycloak.start();
        }
    }

    @Test
    public void shouldConsiderConfiguredStartupTimeout() {
        final int MAX_TIMEOUT = 5;
        Instant start = Instant.now();
        try {
            Duration duration = Duration.ofSeconds(MAX_TIMEOUT);
            try (KeycloakContainer keycloak = new KeycloakContainer().withStartupTimeout(duration)) {
                keycloak.start();
            }
        } catch (ContainerLaunchException ex) {
            Duration observedDuration = Duration.between(start, Instant.now());
            assertTrue(observedDuration.toSeconds() >= MAX_TIMEOUT && observedDuration.toSeconds() < 30,
                String.format("Startup time should consider configured limit of %d seconds, but took %d seconds",
                    MAX_TIMEOUT, observedDuration.toSeconds()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {TEST_REALM_JSON, "/single-test-folder" + TEST_REALM_JSON, "/second-test-folder/first-test-folder" + TEST_REALM_JSON})
    public void shouldImportRealm(final String realmLocation) {
        try (KeycloakContainer keycloak = new KeycloakContainer().withRealmImportFile(realmLocation)) {
            keycloak.start();

            String accountService = given().when().get(keycloak.getAuthServerUrl() + "/realms/test")
                .then().statusCode(200).body("realm", equalTo("test"))
                .extract().path("account-service");

            given().when().get(accountService).then().statusCode(200);
        }
    }

    @Test
    public void shouldImportMultipleRealms() {
        try (KeycloakContainer keycloak = new KeycloakContainer().
            withRealmImportFiles(TEST_REALM_JSON, "/another-realm.json")) {
            keycloak.start();

            String accountService = given().when().get(keycloak.getAuthServerUrl() + "/realms/test")
                .then().statusCode(200).body("realm", equalTo("test"))
                .extract().path("account-service");

            given().when().get(accountService).then().statusCode(200);

            accountService = given().when().get(keycloak.getAuthServerUrl() + "/realms/another")
                .then().statusCode(200).body("realm", equalTo("another"))
                .extract().path("account-service");

            given().when().get(accountService).then().statusCode(200);
        }
    }

    @Test
    public void shouldImportMasterRealmAdmin() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .withBootstrapAdminDisabled()
            .withRealmImportFiles(MASTER_REALM_WITH_ADMIN_USER_JSON)) {
            keycloak.start();

            // Throws because we have imported a different admin user with different password
            assertThrows(NotAuthorizedException.class, () -> keycloak.getKeycloakAdminClient().tokenManager().getAccessToken());

            // Set password from imported realm, see json file
            keycloak.withAdminPassword("password");
            keycloak.getKeycloakAdminClient().tokenManager().getAccessToken();
        }
    }

    @Test
    public void shouldReturnServerInfo() {
        try (KeycloakContainer keycloak = new KeycloakContainer()) {
            keycloak.start();

            checkKeycloakContainerInternals(keycloak);
        }
    }

    @Test
    public void shouldReturnServerInfoNightly() {
        try (KeycloakContainer keycloak = new KeycloakContainer().withNightly()) {
            keycloak.start();
            checkKeycloakContainerInternals(keycloak);
        }
    }

    @Test
    public void shouldUseDifferentAdminCredentials() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .withAdminUsername("foo")
            .withAdminPassword("bar")) {
            keycloak.start();

            checkKeycloakContainerInternals(keycloak);
        }
    }

    @Test
    public void shouldRunOnDifferentContextPath() {
        String contextPath = "/auth";
        try (KeycloakContainer keycloak = new KeycloakContainer().withContextPath(contextPath)) {
            keycloak.start();

            String authServerUrl = keycloak.getAuthServerUrl();
            assertThat(authServerUrl, endsWith(contextPath));

            getWellKnownOpenidConfigurationResponse(authServerUrl);
            checkKeycloakContainerInternals(keycloak);
        }
    }

    @Test
    public void shouldNotExposeMetricsPerDefault() {
        try (KeycloakContainer keycloak = new KeycloakContainer()) {
            keycloak.start();

            given().when().get(keycloak.getMgmtServerUrl() + "/metrics")
                .then().statusCode(404);
        }
    }

    @Test
    public void shouldExposeMetricsWithEnabledMetrics() {
        try (KeycloakContainer keycloak = new KeycloakContainer().withEnabledMetrics()) {
            keycloak.start();

            given().when().get(keycloak.getMgmtServerUrl() + "/metrics")
                .then().statusCode(200);
        }
    }

    @Test
    public void shouldStartKeycloakVerbose() {
        try (KeycloakContainer keycloak = new KeycloakContainer().withVerboseOutput()) {
            keycloak.start();
        }
    }

    @Test
    void shouldOpenRandomDebugPort() throws IOException {
        try (KeycloakContainer keycloak = new KeycloakContainer().withDebug()) {
            keycloak.start();

            testDebugPortAvailable(keycloak.getHost(), keycloak.getDebugPort());
        }
    }

    @Test
    void shouldOpenFixedDebugPort() throws IOException {
        final int fixedDebugPort = findFreePort();
        try (KeycloakContainer keycloak = new KeycloakContainer().withDebugFixedPort(fixedDebugPort, false)) {
            keycloak.start();

            assertThat(keycloak.getDebugPort(), is(fixedDebugPort));
            testDebugPortAvailable(keycloak.getHost(), keycloak.getDebugPort());
        }
    }

    @Test
    void shouldUseCustomCommandPart() {
        try (KeycloakContainer keycloak = new KeycloakContainer().withCustomCommand("--hostname=keycloak.local")) {
            keycloak.start();
            String issuer = getWellKnownOpenidConfigurationResponse(keycloak.getAuthServerUrl()).extract().path("issuer");
            assertThat(issuer, containsString("keycloak.local"));
        }
    }

    @Test
    public void shouldStartKeycloakWithDifferentWaitStrategy() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .waitingFor(KeycloakContainer.LOG_WAIT_STRATEGY)) {
            keycloak.start();
            checkKeycloakContainerInternals(keycloak);
        }
    }

    private static ValidatableResponse getWellKnownOpenidConfigurationResponse(String authServerUrl) {
        return given().when().get(authServerUrl + "/realms/master/.well-known/openid-configuration")
            .then().statusCode(200);
    }

    private void checkKeycloakContainerInternals(KeycloakContainer keycloak) {
        Keycloak keycloakAdminClient = keycloak.getKeycloakAdminClient();
        ServerInfoRepresentation serverInfo = keycloakAdminClient.serverInfo().getInfo();
        assertThat(serverInfo, notNullValue());
        assertThat(serverInfo.getSystemInfo().getVersion(), startsWith(keycloak.getKeycloakDefaultVersion()));
    }

    private static int findFreePort() {
        try (var serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            fail("There is no free port available!");
            return -1;
        }
    }

    private void testDebugPortAvailable(final String debugHost, final int debugPort) throws IOException {
        try (var debugSocket = new Socket()) {
            try {
                debugSocket.connect(new InetSocketAddress(debugHost, debugPort));
            } catch (IOException e) {
                fail(String.format("Debug port %d cannot be reached.", debugPort));
            }
        }
    }
}

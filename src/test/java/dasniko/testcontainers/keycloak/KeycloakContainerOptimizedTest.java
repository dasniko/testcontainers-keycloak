package dasniko.testcontainers.keycloak;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.nio.file.Paths;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

public class KeycloakContainerOptimizedTest {

    public static final String UPDATING_THE_CONFIGURATION = "Updating the configuration and installing your custom providers, if any. Please wait.";
    private static final String TEMPORARY_KEYCLOAK_IMAGE = "localhost/temporary-testcontainer-keycloak-image:latest";
    private KeycloakContainer keycloakContainer;

    @BeforeEach
    void setUp() {
        try (GenericContainer<?> container = new GenericContainer<>(
            new ImageFromDockerfile(TEMPORARY_KEYCLOAK_IMAGE, true)
                .withDockerfile(Paths.get("src/test/resources/Dockerfile"))
                .withTarget("builder"))) {
            try (KeycloakContainer keycloak = new KeycloakContainer(container.getDockerImageName())
                .useTls()
                .withEnv("KC_HOSTNAME_STRICT", "false")
                .withOptimizedFlag()) {
                keycloakContainer = keycloak;
            }
        }
    }

    @Test
    public void shouldBeAvailableOnHTTPS() {
        keycloakContainer.start();
        RestAssured.useRelaxedHTTPSValidation();
        assertThat(keycloakContainer.getAuthServerUrl(), startsWith("https://"));
        given()
            .when().get(keycloakContainer.getAuthServerUrl())
            .then().statusCode(200);
    }

    @Test
    public void shouldBeAvailableWithProfileProd() {
        keycloakContainer.start();
        assertThat(keycloakContainer.getLogs(), containsString("Profile prod activated."));
    }

    @Test
    public void shouldBeAvailableWithoutInstallingProviderLogMessage() {
        keycloakContainer.start();
        assertThat(keycloakContainer.getLogs(),
            not(containsString(UPDATING_THE_CONFIGURATION)));
    }

    @Test
    public void shouldStartWithProviderInstallationLogMessageWhenOptimizedIsNotSet() {
        try (KeycloakContainer keycloak = new KeycloakContainer()) {
            keycloak.start();
            assertThat(keycloak.getLogs(),
                containsString(UPDATING_THE_CONFIGURATION));
        }
    }
}

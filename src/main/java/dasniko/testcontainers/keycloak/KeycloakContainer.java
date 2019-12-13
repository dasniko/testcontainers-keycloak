package dasniko.testcontainers.keycloak;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainer extends GenericContainer<KeycloakContainer> {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak";
    private static final String KEYCLOAK_VERSION = "8.0.1";

    private static final int KEYCLOAK_PORT = 8080;

    private static final String KEYCLOAK_ADMIN_USER = "admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "admin";
    private static final String KEYCLOAK_AUTH_PATH = "/auth";

    private String importFile;

    private Keycloak keycloakAdminClient;

    public KeycloakContainer() {
        this(KEYCLOAK_IMAGE + ":" + KEYCLOAK_VERSION);
    }

    /**
     * Create a KeycloakContainer by passing the full docker image name
     *
     * @param dockerImageName Full docker image name, e.g. quay.io/keycloak/keycloak:8.0.1
     */
    public KeycloakContainer(String dockerImageName) {
        super(dockerImageName);
        addExposedPort(KEYCLOAK_PORT);
        setWaitStrategy(Wait
            .forHttp(KEYCLOAK_AUTH_PATH)
            .withStartupTimeout(Duration.ofMinutes(2))
        );
        withLogConsumer(new Slf4jLogConsumer(logger()));
    }

    @Override
    protected void configure() {
        withCommand("-c standalone.xml"); // don't start infinispan cluster

        withEnv("KEYCLOAK_USER", KEYCLOAK_ADMIN_USER);
        withEnv("KEYCLOAK_PASSWORD", KEYCLOAK_ADMIN_PASSWORD);

        if (importFile != null) {
            String importFileInContainer = "/tmp/" + importFile;
            withCopyFileToContainer(MountableFile.forClasspathResource(importFile), importFileInContainer);
            withEnv("KEYCLOAK_IMPORT", importFileInContainer);
        }
    }

    @Override
    public void stop() {
        if (keycloakAdminClient != null) {
            keycloakAdminClient.close();
            keycloakAdminClient = null;
        }

        super.stop();
    }

    public KeycloakContainer withRealmImportFile(String importFile) {
        this.importFile = importFile;
        return self();
    }

    public String getAuthServerUrl() {
        return String.format("http://%s:%s%s", getContainerIpAddress(), getFirstMappedPort(), KEYCLOAK_AUTH_PATH);
    }

    public String getKeycloakVersion() {
        return KEYCLOAK_VERSION;
    }

    public Keycloak getKeycloakAdminClient() {
        if (keycloakAdminClient == null) {
            keycloakAdminClient = KeycloakBuilder.builder()
                .realm("master")
                .serverUrl(getAuthServerUrl())
                .clientId("admin-cli")
                .username(KEYCLOAK_ADMIN_USER)
                .password(KEYCLOAK_ADMIN_PASSWORD)
                .build();
        }

        return keycloakAdminClient;
    }
}

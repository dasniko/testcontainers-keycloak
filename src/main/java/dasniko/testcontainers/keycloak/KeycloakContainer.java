package dasniko.testcontainers.keycloak;

import lombok.Setter;
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

    @Setter
    private String importFile;

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

    public KeycloakContainer withRealmImportFile(String importFile) {
        this.importFile = importFile;
        return self();
    }

    public String getAuthServerUrl() {
        return String.format("http://%s:%s%s", getContainerIpAddress(), getFirstMappedPort(), KEYCLOAK_AUTH_PATH);
    }
}

package dasniko.testcontainers.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.importer.ExplodedImporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainer extends GenericContainer<KeycloakContainer> {

    public static final String MASTER_REALM = "master";
    public static final String ADMIN_CLI_CLIENT = "admin-cli";

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak-x";
    private static final String KEYCLOAK_VERSION = "15.0.2";

    private static final int KEYCLOAK_PORT_HTTP = 8080;
    private static final int KEYCLOAK_PORT_HTTPS = 8443;
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(2);

    private static final String KEYCLOAK_ADMIN_USER = "admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "admin";
    private static final String KEYCLOAK_AUTH_PATH = "/";

    private static final String DEFAULT_PROVIDERS_NAME = "providers.jar";

    private static final String DEFAULT_KEYCLOAK_PROVIDERS_LOCATION = "/opt/jboss/keycloak/providers";

    private String adminUsername = KEYCLOAK_ADMIN_USER;
    private String adminPassword = KEYCLOAK_ADMIN_PASSWORD;

    private final Set<String> importFiles;
    private String tlsKeystoreFilename;
    private String tlsKeystorePassword;
    private boolean useTls = false;

    private Duration startupTimeout = DEFAULT_STARTUP_TIMEOUT;

    private String providerClassLocation;

    /**
     * Create a KeycloakContainer with default image and version tag
     */
    public KeycloakContainer() {
        this(KEYCLOAK_IMAGE + ":" + KEYCLOAK_VERSION);
    }

    /**
     * Create a KeycloakContainer by passing the full docker image name
     *
     * @param dockerImageName Full docker image name, e.g. quay.io/keycloak/keycloak-x:15.0.2
     */
    public KeycloakContainer(String dockerImageName) {
        super(DockerImageName.parse(dockerImageName));
        withExposedPorts(KEYCLOAK_PORT_HTTP, KEYCLOAK_PORT_HTTPS);
        importFiles = new HashSet<>();
        withLogConsumer(new Slf4jLogConsumer(logger()));
    }

    @Override
    protected void configure() {
        withCommand(
//            "start-dev", // start the server w/o https in dev mode, local caching only
            "--auto-config",
            "--profile=dev" // start the server w/o https in dev mode, local caching only
        );

        setWaitStrategy(Wait
            .forHttp(KEYCLOAK_AUTH_PATH)
            .forPort(KEYCLOAK_PORT_HTTP)
            .withStartupTimeout(startupTimeout)
        );

        withEnv("KEYCLOAK_ADMIN", adminUsername);
        withEnv("KEYCLOAK_ADMIN_PASSWORD", adminPassword);

        if (useTls && isNotBlank(tlsKeystoreFilename)) {
            String keystoreFileInContainer = "/opt/jboss/keycloak/conf/server.keystore";
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsKeystoreFilename), keystoreFileInContainer);
            withEnv("KC_HTTPS_CERTIFICATE_KEY_STORE_PASSWORD", tlsKeystorePassword);
        }

        if (providerClassLocation != null) {
            createKeycloakExtensionProvider(providerClassLocation);
        }
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        if (!importFiles.isEmpty()) {
            logger().info("Connect to Keycloak container to import given realm files.");
            Keycloak kcAdmin =
                Keycloak.getInstance(getAuthServerUrl(), MASTER_REALM, getAdminUsername(), getAdminPassword(), ADMIN_CLI_CLIENT);
            try {
                for (String importFile : importFiles) {
                    logger().info("Importing realm from file {}", importFile);
                    kcAdmin.realms().create(
                        new ObjectMapper().readValue(
                            Thread.currentThread().getContextClassLoader().getResource(importFile),
                            RealmRepresentation.class
                        )
                    );
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Maps the provided {@code extensionClassFolder} as an exploded providers.jar to the Keycloak providers folder.
     *
     * @param extensionClassFolder a path relative to the current classpath root.
     */
    public void createKeycloakExtensionProvider(String extensionClassFolder) {
        createKeycloakExtensionDeployment(DEFAULT_KEYCLOAK_PROVIDERS_LOCATION, DEFAULT_PROVIDERS_NAME, extensionClassFolder);
    }

    /**
     * Maps the provided {@code extensionClassFolder} as an exploded extension.jar to the {@code deploymentLocation}.
     *
     * @param deploymentLocation   the target deployments location of the Keycloak server.
     * @param extensionName        the name suffix of the created extension.
     * @param extensionClassFolder a path relative to the current classpath root.
     */
    protected void createKeycloakExtensionDeployment(String deploymentLocation, String extensionName, String extensionClassFolder) {

        Objects.requireNonNull(deploymentLocation, "deploymentLocation");
        Objects.requireNonNull(extensionName, "extensionName");
        Objects.requireNonNull(extensionClassFolder, "extensionClassFolder");

        String classesLocation = resolveExtensionClassLocation(extensionClassFolder);
        if (new File(classesLocation).exists()) {
            final File file;
            try {
                file = Files.createTempFile(Path.of("target"), "keycloak", ".jar").toFile();
                file.setReadable(true, false);
                file.deleteOnExit();
                ShrinkWrap.create(JavaArchive.class, extensionName)
                    .as(ExplodedImporter.class)
                    .importDirectory(classesLocation)
                    .as(ZipExporter.class)
                    .exportTo(file, true);
                addFileSystemBind(file.getAbsolutePath(), deploymentLocation + "/" + extensionName, BindMode.READ_ONLY, SelinuxContext.SINGLE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    protected String resolveExtensionClassLocation(String extensionClassFolder) {
        String moduleFolder = MountableFile.forClasspathResource(".").getResolvedPath() + "/../../";
        return moduleFolder + extensionClassFolder;
    }

    public KeycloakContainer withRealmImportFile(String importFile) {
        this.importFiles.add(importFile);
        return self();
    }

    public KeycloakContainer withRealmImportFiles(String... files) {
        Arrays.stream(files).forEach(this::withRealmImportFile);
        return self();
    }

    public KeycloakContainer withAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
        return self();
    }

    public KeycloakContainer withAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
        return self();
    }

    /**
     * Exposes the given classes location as an exploded providers.jar.
     *
     * @param classesLocation a classes location relative to the current classpath root.
     */
    public KeycloakContainer withProviderClassesFrom(String classesLocation) {
        this.providerClassLocation = classesLocation;
        return self();
    }

    public KeycloakContainer withStartupTimeout(Duration startupTimeout) {
        this.startupTimeout = startupTimeout;
        return self();
    }

    public KeycloakContainer useTls() {
        // server.keystore is provided with this testcontainer
        return useTls("tls.jks", "changeit");
    }

    public KeycloakContainer useTls(String tlsKeystoreFilename, String tlsKeystorePassword) {
        this.tlsKeystoreFilename = tlsKeystoreFilename;
        this.tlsKeystorePassword = tlsKeystorePassword;
        this.useTls = true;
        return self();
    }

    public String getAuthServerUrl() {
        return String.format("http%s://%s:%s%s", useTls ? "s" : "", getContainerIpAddress(),
            useTls ? getMappedPort(KEYCLOAK_PORT_HTTPS) : getMappedPort(KEYCLOAK_PORT_HTTP), KEYCLOAK_AUTH_PATH);
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public int getHttpPort() {
        return getMappedPort(KEYCLOAK_PORT_HTTP);
    }

    public int getHttpsPort() {
        return getMappedPort(KEYCLOAK_PORT_HTTPS);
    }

    public Duration getStartupTimeout() {
        return startupTimeout;
    }

    protected String getKeycloakVersion() {
        return KEYCLOAK_VERSION;
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}

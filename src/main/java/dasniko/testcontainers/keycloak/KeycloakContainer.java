package dasniko.testcontainers.keycloak;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainer extends GenericContainer<KeycloakContainer> {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak-x";
    private static final String KEYCLOAK_VERSION = "15.0.2";

    private static final int KEYCLOAK_PORT_HTTP = 8080;
    private static final int KEYCLOAK_PORT_HTTPS = 8443;
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(1);

    private static final String KEYCLOAK_ADMIN_USER = "admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "admin";
    private static final String KEYCLOAK_AUTH_PATH = "/";

    private static final String DEFAULT_EXTENSION_NAME = "extensions.jar";
    private static final String DEFAULT_PROVIDERS_NAME = "providers.jar";

    // for Keycloak-X this will be /opt/jboss/keycloak/providers
    private static final String DEFAULT_KEYCLOAK_DEPLOYMENTS_LOCATION = "/opt/jboss/keycloak/standalone/deployments";
    private static final String DEFAULT_KEYCLOAK_PROVIDERS_LOCATION = "/opt/jboss/keycloak/providers";

    private String adminUsername = KEYCLOAK_ADMIN_USER;
    private String adminPassword = KEYCLOAK_ADMIN_PASSWORD;

    private final Set<String> importFiles;
    private String tlsCertFilename;
    private String tlsKeyFilename;
    private boolean useTls = false;

    private Duration startupTimeout = DEFAULT_STARTUP_TIMEOUT;

    private String extensionClassLocation;
    private String providerClassLocation;

    private static final Transferable WILDFLY_DEPLOYMENT_TRIGGER_FILE_CONTENT = Transferable.of("true".getBytes(StandardCharsets.UTF_8));
    private final Set<String> wildflyDeploymentTriggerFiles = new HashSet<>();

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
//        withLogConsumer(new Slf4jLogConsumer(logger()));
    }

    @Override
    protected void configure() {
        withCommand(
            "start-dev" // start the server w/o https in dev mode, local caching only
        );

        setWaitStrategy(Wait
            .forHttp(KEYCLOAK_AUTH_PATH)
            .forPort(KEYCLOAK_PORT_HTTP)
            .withStartupTimeout(startupTimeout)
        );

        withEnv("KEYCLOAK_ADMIN", adminUsername);
        withEnv("KEYCLOAK_ADMIN_PASSWORD", adminPassword);

        if (useTls && isNotBlank(tlsCertFilename) && isNotBlank(tlsKeyFilename)) {
            String certFileInContainer = "/etc/x509/https/tls.crt";
            String keyFileInContainer = "/etc/x509/https/tls.key";
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsCertFilename), certFileInContainer);
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsKeyFilename), keyFileInContainer);
        }

        List<String> filesInContainer = new ArrayList<>();
        for (String importFile : importFiles) {
            String importFileInContainer = "/tmp/" + importFile;
            filesInContainer.add(importFileInContainer);
            withCopyFileToContainer(MountableFile.forClasspathResource(importFile), importFileInContainer);
        }

        if (!importFiles.isEmpty()) {
            withEnv("KEYCLOAK_IMPORT", String.join(",", filesInContainer));
        }

        if (extensionClassLocation != null) {
            createKeycloakExtensionDeployment(extensionClassLocation);
        }

        if (providerClassLocation != null) {
            createKeycloakExtensionProvider(providerClassLocation);
        }
    }

    /**
     * Maps the provided {@code extensionClassFolder} as an exploded extension.jar to the Keycloak deployments folder.
     *
     * @param extensionClassFolder a path relative to the current classpath root.
     */
    public void createKeycloakExtensionDeployment(String extensionClassFolder) {
        createKeycloakExtensionDeployment(DEFAULT_KEYCLOAK_DEPLOYMENTS_LOCATION, DEFAULT_EXTENSION_NAME, extensionClassFolder);
    }

    /**
     * Maps the provided {@code extensionClassFolder} as an exploded providers.jar to the Keycloak providers folder.
     *
     * @param extensionClassFolder a path relative to the current classpath root.
     */
    public void createKeycloakExtensionProvider(String extensionClassFolder) {
        createKeycloakExtensionDeployment(DEFAULT_KEYCLOAK_PROVIDERS_LOCATION, DEFAULT_EXTENSION_NAME, extensionClassFolder);
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

        if (!new File(classesLocation).exists()) {
            return;
        }

        String explodedFolderName = extensionClassFolder.hashCode() + "-" + extensionName;
        String explodedFolderExtensionsJar = deploymentLocation + "/" + explodedFolderName;
        addFileSystemBind(classesLocation, explodedFolderExtensionsJar, BindMode.READ_WRITE, SelinuxContext.SINGLE);

        boolean wildflyDeployment = deploymentLocation.contains("/standalone/deployments");
        if (wildflyDeployment) {
            registerWildflyDeploymentTriggerFile(deploymentLocation, explodedFolderName);

            // wait for extension deployment
            setWaitStrategy(createCombinedWaitAllStrategy(Wait.forLogMessage(".* Deployed \"" + explodedFolderName + "\" .*", 1)));
        }
    }

    /**
     * Creates a {@link WaitAllStrategy} based on the current {@link #getWaitStrategy()} if present followed by the given {@link WaitStrategy}.
     */
    private WaitAllStrategy createCombinedWaitAllStrategy(WaitStrategy waitStrategy) {
        WaitAllStrategy waitAll = new WaitAllStrategy();
        // startup timeout needs to be configured before calling .withStrategy(..) due to implementation in testcontainers.
        waitAll.withStartupTimeout(startupTimeout);
        WaitStrategy currentWaitStrategy = getWaitStrategy();
        if (currentWaitStrategy != null) {
            waitAll.withStrategy(currentWaitStrategy);
        }
        waitAll.withStrategy(waitStrategy);
        return waitAll;
    }

    /**
     * Registers a {@code extensions.jar.dodeploy} file to be created at container startup.
     */
    private void registerWildflyDeploymentTriggerFile(String deploymentLocation, String extensionArtifact) {
        String triggerFileName = extensionArtifact + ".dodeploy";
        wildflyDeploymentTriggerFiles.add(deploymentLocation + "/" + triggerFileName);
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        createWildflyDeploymentTriggerFiles();
    }

    @Override
    protected void containerIsStopping(InspectContainerResponse containerInfo) {
        wildflyDeploymentTriggerFiles.clear();
    }

    /**
     * Creates a new Wildfly {@code extensions.jar.dodeploy} deployment trigger file to ensure the exploded extension
     * folder is deployed on container startup.
     */
    private void createWildflyDeploymentTriggerFiles() {
        wildflyDeploymentTriggerFiles.forEach(deploymentTriggerFile ->
            copyFileToContainer(WILDFLY_DEPLOYMENT_TRIGGER_FILE_CONTENT, deploymentTriggerFile));
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
     * Exposes the given classes location as an exploded extension.jar.
     *
     * @param classesLocation a classes location relative to the current classpath root.
     */
    public KeycloakContainer withExtensionClassesFrom(String classesLocation) {
        this.extensionClassLocation = classesLocation;
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

    public KeycloakContainer useTls() {
        // tls.crt and tls.key are provided with this testcontainer
        return useTls("tls.crt", "tls.key");
    }

    public KeycloakContainer withStartupTimeout(Duration startupTimeout) {
        this.startupTimeout = startupTimeout;
        return self();
    }

    public KeycloakContainer useTls(String tlsCertFilename, String tlsKeyFilename) {
        this.tlsCertFilename = tlsCertFilename;
        this.tlsKeyFilename = tlsKeyFilename;
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

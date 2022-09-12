package dasniko.testcontainers.keycloak;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.keycloak.admin.client.Keycloak;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainer extends GenericContainer<KeycloakContainer> {

    public static final String MASTER_REALM = "master";
    public static final String ADMIN_CLI_CLIENT = "admin-cli";

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak";
    private static final String KEYCLOAK_VERSION = "19.0.1";

    private static final int KEYCLOAK_PORT_HTTP = 8080;
    private static final int KEYCLOAK_PORT_HTTPS = 8443;
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(2);

    private static final String KEYCLOAK_ADMIN_USER = "admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "admin";
    private static final String KEYCLOAK_AUTH_PATH = "/auth";

    private static final String DB_VENDOR = "h2";

    private static final String DEFAULT_EXTENSION_NAME = "extensions.jar";
    private static final String DEFAULT_PROVIDERS_NAME = "providers.jar";

    private static final String DEFAULT_KEYCLOAK_DEPLOYMENTS_LOCATION = "/opt/jboss/keycloak/standalone/deployments";
    private static final String DEFAULT_KEYCLOAK_PROVIDERS_LOCATION = "/opt/jboss/keycloak/providers";

    private String adminUsername = KEYCLOAK_ADMIN_USER;
    private String adminPassword = KEYCLOAK_ADMIN_PASSWORD;

    private String dbVendor = DB_VENDOR;
    private final Set<String> importFiles;
    private final Set<String> startupScripts;
    private String tlsCertFilename;
    private String tlsKeyFilename;
    private boolean useTls = false;

    private Duration startupTimeout = DEFAULT_STARTUP_TIMEOUT;

    private String extensionClassLocation;
    private String providerClassLocation;
    private List<File> providerLibsLocations;

    private static final Transferable WILDFLY_DEPLOYMENT_TRIGGER_FILE_CONTENT = Transferable.of("true".getBytes(StandardCharsets.UTF_8));
    private final Set<String> wildflyDeploymentTriggerFiles = new HashSet<>();

    public KeycloakContainer() {
        this(KEYCLOAK_IMAGE + ":" + KEYCLOAK_VERSION + "-legacy");
    }

    /**
     * Create a KeycloakContainer by passing the full docker image name
     *
     * @param dockerImageName Full docker image name, e.g. quay.io/keycloak/keycloak:8.0.1
     */
    public KeycloakContainer(String dockerImageName) {
        super(dockerImageName);
        withExposedPorts(KEYCLOAK_PORT_HTTP, KEYCLOAK_PORT_HTTPS);
        importFiles = new HashSet<>();
        startupScripts = new HashSet<>();
        withLogConsumer(new Slf4jLogConsumer(logger()));
    }

    @Override
    protected void configure() {
        setCommand(
            "-c standalone.xml", // don't start infinispan cluster
            "-b 0.0.0.0", // ensure proper binding
            "-Dkeycloak.profile.feature.upload_scripts=enabled" // enable script uploads
        );

        setWaitStrategy(Wait
            .forHttp(KEYCLOAK_AUTH_PATH)
            .forPort(KEYCLOAK_PORT_HTTP)
            .withStartupTimeout(startupTimeout)
        );

        withEnv("KEYCLOAK_USER", adminUsername);
        withEnv("KEYCLOAK_PASSWORD", adminPassword);

        withEnv("DB_VENDOR", dbVendor);

        if (useTls && isNotBlank(tlsCertFilename) && isNotBlank(tlsKeyFilename)) {
            String certFileInContainer = "/etc/x509/https/tls.crt";
            String keyFileInContainer = "/etc/x509/https/tls.key";
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsCertFilename), certFileInContainer);
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsKeyFilename), keyFileInContainer);
        }

        List<String> importFilesInContainer = new ArrayList<>();
        for (String importFile : importFiles) {
            String importFileInContainer = "/tmp/" + importFile;
            importFilesInContainer.add(importFileInContainer);
            withCopyFileToContainer(MountableFile.forClasspathResource(importFile), importFileInContainer);
        }

        if (!importFiles.isEmpty()) {
            withEnv("KEYCLOAK_IMPORT", String.join(",", importFilesInContainer));
        }

        for (String startupScript : startupScripts) {
            String startupScriptInContainer = "/opt/jboss/startup-scripts/" + startupScript;
            withCopyFileToContainer(MountableFile.forClasspathResource(startupScript), startupScriptInContainer);
        }

        if (extensionClassLocation != null) {
            createKeycloakExtensionDeployment(extensionClassLocation);
        }

        if (providerClassLocation != null) {
            createKeycloakExtensionProvider(providerClassLocation);
        }

        if (providerLibsLocations != null) {
            providerLibsLocations.forEach(file -> {
                String containerPath = DEFAULT_KEYCLOAK_PROVIDERS_LOCATION + "/" + file.getName();
                withCopyFileToContainer(MountableFile.forHostPath(file.getAbsolutePath()), containerPath);
            });
        }
    }

    @Override
    public KeycloakContainer withCommand(String cmd) {
        logger().warn("You are trying to set custom container commands, which are currently not supported by this Testcontainer.");
        return self();
    }

    @Override
    public KeycloakContainer withCommand(String... commandParts) {
        logger().warn("You are trying to set custom container commands, which are currently not supported by this Testcontainer.");
        return self();
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

        Path extensionClassPath = Paths.get(classesLocation);
        if (!Files.exists(extensionClassPath)) {
            return;
        }

        String explodedFolderName = extensionClassFolder.hashCode() + "-" + extensionName;
        String explodedFolderExtensionsJar = deploymentLocation + "/" + explodedFolderName;

        try (Stream<Path> extensionPathStream = Files.walk(extensionClassPath)) {
            extensionPathStream.forEach(extPath -> {
                if (!Files.isDirectory(extPath)) {
                    String to = explodedFolderExtensionsJar + "/" + extensionClassPath.relativize(extPath.toAbsolutePath());
                    withCopyFileToContainer(MountableFile.forHostPath(extPath), to);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

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
        return Paths.get(MountableFile.forClasspathResource(".").getResolvedPath())
            .getParent()
            .getParent()
            .resolve(extensionClassFolder)
            .toString();
    }

    public KeycloakContainer withRealmImportFile(String importFile) {
        this.importFiles.add(importFile);
        return self();
    }

    public KeycloakContainer withRealmImportFiles(String... files) {
        Arrays.stream(files).forEach(this::withRealmImportFile);
        return self();
    }

    @Deprecated
    public KeycloakContainer withStartupScripts(String... startupScripts) {
        this.startupScripts.addAll(Arrays.asList(startupScripts));
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
     * @deprecated This method will be removed in next major version of this project,
     * as it doesn't make much sense in testing environment to use some other stateful system,
     * as tests should be independend from other environment.
     * Also, this option was never officially documented and only here for internal meanings.
     */
    @Deprecated
    public KeycloakContainer withDbVendor(String dbVendor) {
        this.dbVendor = dbVendor;
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

    public KeycloakContainer withProviderLibsFrom(List<File> libs) {
        this.providerLibsLocations = libs;
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

    public Keycloak getKeycloakAdminClient() {
        return Keycloak.getInstance(getAuthServerUrl(), MASTER_REALM, getAdminUsername(), getAdminPassword(), ADMIN_CLI_CLIENT);
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

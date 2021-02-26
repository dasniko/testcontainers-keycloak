package dasniko.testcontainers.keycloak;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Objects;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainer extends GenericContainer<KeycloakContainer> {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak";
    private static final String KEYCLOAK_VERSION = "12.0.1";

    private static final int KEYCLOAK_PORT_HTTP = 8080;
    private static final int KEYCLOAK_PORT_HTTPS = 8443;

    private static final String KEYCLOAK_ADMIN_USER = "admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "admin";
    private static final String KEYCLOAK_AUTH_PATH = "/auth";

    private static final String DEFAULT_EXTENSION_NAME = "extensions.jar";

    // for Keycloak-X this will be /opt/jboss/keycloak/providers
    private static final String DEFAULT_KEYCLOAK_DEPLOYMENTS_LOCATION = "/opt/jboss/keycloak/standalone/deployments";

    private String adminUsername = KEYCLOAK_ADMIN_USER;
    private String adminPassword = KEYCLOAK_ADMIN_PASSWORD;

    private String importFile;
    private String tlsCertFilename;
    private String tlsKeyFilename;
    private boolean useTls = false;

    private String extensionClassLocation;

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
        withExposedPorts(KEYCLOAK_PORT_HTTP, KEYCLOAK_PORT_HTTPS);
        setWaitStrategy(Wait
            .forHttp(KEYCLOAK_AUTH_PATH)
            .forPort(KEYCLOAK_PORT_HTTP)
            .withStartupTimeout(Duration.ofMinutes(2))
        );
//        withLogConsumer(new Slf4jLogConsumer(logger()));
    }

    @Override
    protected void configure() {
        withCommand(
            "-c standalone.xml", // don't start infinispan cluster
            "-b 0.0.0.0", // ensure proper binding
            "-Dkeycloak.profile.feature.upload_scripts=enabled" // enable script uploads
        );

        withEnv("KEYCLOAK_USER", adminUsername);
        withEnv("KEYCLOAK_PASSWORD", adminPassword);

        if (useTls && isNotBlank(tlsCertFilename) && isNotBlank(tlsKeyFilename)) {
            String certFileInContainer = "/etc/x509/https/tls.crt";
            String keyFileInContainer = "/etc/x509/https/tls.key";
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsCertFilename), certFileInContainer);
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsKeyFilename), keyFileInContainer);
        }

        if (importFile != null) {
            String importFileInContainer = "/tmp/" + importFile;
            withCopyFileToContainer(MountableFile.forClasspathResource(importFile), importFileInContainer);
            withEnv("KEYCLOAK_IMPORT", importFileInContainer);
        }

        if (extensionClassLocation != null) {
            createKeycloakExtensionDeployment(extensionClassLocation);
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
     * Maps the provided {@code extensionClassFolder} as an exploded extension.jar to the {@code deploymentLocation}.
     *
     * @param deploymentLocation the target deployments location of the Keycloak server.
     * @param extensionName the name suffix of the created extension.
     * @param extensionClassFolder  a path relative to the current classpath root.
     */
    protected void createKeycloakExtensionDeployment(String deploymentLocation, String extensionName, String extensionClassFolder) {

        Objects.requireNonNull(deploymentLocation, "deploymentLocation");
        Objects.requireNonNull(extensionName, "extensionName");
        Objects.requireNonNull(extensionClassFolder, "extensionClassFolder");

        String classesLocation = resolveExtensionClassLocation(extensionClassFolder);

        if (!new File(classesLocation).exists()) {
            return;
        }

        String uniqueExtensionNameForExtensionClassFolder = extensionClassFolder.hashCode() + "-" + extensionName;
        String explodedFolderExtensionsJar = deploymentLocation + "/" + uniqueExtensionNameForExtensionClassFolder;
        addFileSystemBind(classesLocation, explodedFolderExtensionsJar, BindMode.READ_WRITE, SelinuxContext.SINGLE);

        boolean wildflyDeployment = deploymentLocation.contains("/standalone/deployments");
        if (wildflyDeployment) {
            createDeploymentTriggerFileForWildfly(explodedFolderExtensionsJar);
        }
    }

    private void createDeploymentTriggerFileForWildfly(String explodedFolderExtensionsJar) {

        String deploymentTriggerContainerFile = explodedFolderExtensionsJar + ".dodeploy";
        try {
            // Refactor once test-containers support mounting a string as file
            File deploymentTriggerFile = File.createTempFile("kc-tc-deploy", null);
            deploymentTriggerFile.deleteOnExit();
            Files.write(deploymentTriggerFile.toPath(), "true".getBytes(StandardCharsets.UTF_8));
            withFileSystemBind(deploymentTriggerFile.getAbsolutePath(), deploymentTriggerContainerFile, BindMode.READ_ONLY);
        } catch (IOException e) {
            throw new RuntimeException("Could not create extensions deployment trigger file", e);
        }
    }

    protected String resolveExtensionClassLocation(String extensionClassFolder) {
        String moduleFolder = MountableFile.forClasspathResource(".").getResolvedPath() + "/../../";
        return moduleFolder + extensionClassFolder;
    }

    public KeycloakContainer withRealmImportFile(String importFile) {
        this.importFile = importFile;
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
     * @param classesLocation a classes location relative to the current classpath root.
     */
    public KeycloakContainer withExtensionClassesFrom(String classesLocation) {
        this.extensionClassLocation = classesLocation;
        return self();
    }

    public KeycloakContainer useTls() {
        // tls.crt and tls.key are provided with this testcontainer
        return useTls("tls.crt", "tls.key");
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

    protected String getKeycloakVersion() {
        return KEYCLOAK_VERSION;
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

}

package dasniko.testcontainers.keycloak;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainer extends GenericContainer<KeycloakContainer> {

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

    private final String dockerImageName;
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
        this.dockerImageName = dockerImageName;
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

        if (!importFiles.isEmpty()) {
            String importDir = "/tmp/import";
            ImageFromDockerfile newBaseImage = new ImageFromDockerfile();
            setImage(newBaseImage.withDockerfileFromBuilder(builder -> {
                builder.from(dockerImageName);
                for (String importFile : importFiles) {
                    String importFileInContainer = importDir + "/" + importFile;
                    newBaseImage.withFileFromClasspath(importFile, importFile);
                    builder.copy(importFile, importFileInContainer);
                }
                builder.run("/opt/jboss/keycloak/bin/kc.sh import --dir=" + importDir + " --profile=dev || true").build();
            }));
        }

        if (providerClassLocation != null) {
            createKeycloakExtensionProvider(providerClassLocation);
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
        if (!new File(classesLocation).exists()) {
            return;
        }

        String providerFileName = extensionClassFolder.hashCode() + "-" + extensionName;
        String localProviderFilePath = "target/" + providerFileName;
        String deployedProviderFilePath = deploymentLocation + "/" + providerFileName;

        try {
            JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(localProviderFilePath));
            jar(new File(classesLocation), jarOut, classesLocation);
            jarOut.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        addFileSystemBind(localProviderFilePath, deployedProviderFilePath, BindMode.READ_WRITE, SelinuxContext.SINGLE);
    }

    private void jar(File source, JarOutputStream target, String rootPath) throws IOException {
        BufferedInputStream in = null;
        try {
            if (source.isDirectory()) {
                String name = source.getPath();
                if (!name.isEmpty()) {
                    if (!name.endsWith("/")) {
                        name += "/";
                    }
                    JarEntry entry = new JarEntry(name.substring(rootPath.length()));
                    target.putNextEntry(entry);
                    target.closeEntry();
                }
                for (File nestedFile: Objects.requireNonNull(source.listFiles())) {
                    jar(nestedFile, target, rootPath);
                }
                return;
            }

            JarEntry entry = new JarEntry(source.getPath().substring(rootPath.length()));
            target.putNextEntry(entry);
            in = new BufferedInputStream(new FileInputStream(source));

            byte[] bytes = new byte[1024];
            int length;
            while ((length = in.read(bytes)) >= 0) {
                target.write(bytes, 0, length);
            }
            target.closeEntry();
        }
        finally {
            if (in != null) {
                in.close();
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

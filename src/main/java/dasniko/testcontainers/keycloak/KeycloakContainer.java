/*
 * Copyright (c) 2021 Niko Köbler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dasniko.testcontainers.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.importer.ExplodedImporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.keycloak.admin.client.JacksonProvider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainer extends GenericContainer<KeycloakContainer> {

    public static final String MASTER_REALM = "master";
    public static final String ADMIN_CLI_CLIENT = "admin-cli";

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak";
    private static final String KEYCLOAK_VERSION = "17.0.0";

    private static final int KEYCLOAK_PORT_HTTP = 8080;
    private static final int KEYCLOAK_PORT_HTTPS = 8443;
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(2);

    private static final String KEYCLOAK_ADMIN_USER = "admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "admin";
    private static final String KEYCLOAK_CONTEXT_PATH = "/";

    private static final String DEFAULT_KEYCLOAK_PROVIDERS_NAME = "providers.jar";
    private static final String DEFAULT_KEYCLOAK_PROVIDERS_LOCATION = "/opt/keycloak/providers";

    private static final String KEYSTORE_FILE_IN_CONTAINER = "/opt/keycloak/conf/server.keystore";

    private String adminUsername = KEYCLOAK_ADMIN_USER;
    private String adminPassword = KEYCLOAK_ADMIN_PASSWORD;
    private String contextPath = KEYCLOAK_CONTEXT_PATH;

    private final Set<String> importFiles;
    private String tlsCertificateFilename;
    private String tlsCertificateKeyFilename;
    private String tlsKeystoreFilename;
    private String tlsKeystorePassword;
    private boolean useTls = false;

    private String[] featuresEnabled = null;
    private String[] featuresDisabled = null;

    private Duration startupTimeout = DEFAULT_STARTUP_TIMEOUT;

    private String providerClassLocation;
    private List<File> providerLibsLocations;

    /**
     * Create a KeycloakContainer with default image and version tag
     */
    public KeycloakContainer() {
        this(KEYCLOAK_IMAGE + ":" + KEYCLOAK_VERSION);
    }

    /**
     * Create a KeycloakContainer by passing the full docker image name
     *
     * @param dockerImageName Full docker image name, e.g. quay.io/keycloak/keycloak:17.0.0
     */
    public KeycloakContainer(String dockerImageName) {
        super(dockerImageName);
        withExposedPorts(KEYCLOAK_PORT_HTTP, KEYCLOAK_PORT_HTTPS);
        importFiles = new HashSet<>();
        withLogConsumer(new Slf4jLogConsumer(logger()));
    }

    @Override
    protected void configure() {
        List<String> commandParts = new ArrayList<>();
        commandParts.add("start");
        commandParts.add("--auto-build");
        commandParts.add("--cache=local");
        commandParts.add("--http-enabled=true");
        commandParts.add("--hostname-strict=false");
        commandParts.add("--hostname-strict-https=false");

        if (!contextPath.equals(KEYCLOAK_CONTEXT_PATH)) {
            commandParts.add("--http-relative-path=" + contextPath);
        }

        if (featuresEnabled != null) {
            commandParts.add("--features=" + String.join(",", featuresEnabled));
        }

        if (featuresDisabled != null) {
            commandParts.add("--features-disabled=" + String.join(",", featuresDisabled));
        }

        setWaitStrategy(Wait
            .forHttp(contextPath)
            .forPort(KEYCLOAK_PORT_HTTP)
            .withStartupTimeout(startupTimeout)
        );

        withEnv("KEYCLOAK_ADMIN", adminUsername);
        withEnv("KEYCLOAK_ADMIN_PASSWORD", adminPassword);

        if (useTls && isNotBlank(tlsCertificateFilename)) {
            String tlsCertFilePath = "/opt/keycloak/conf/tls.crt";
            String tlsCertKeyFilePath = "/opt/keycloak/conf/tls.key";
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsCertificateFilename), tlsCertFilePath);
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsCertificateKeyFilename), tlsCertKeyFilePath);
            commandParts.add("--https-certificate-file=" + tlsCertFilePath);
            commandParts.add("--https-certificate-key-file=" + tlsCertKeyFilePath);
        } else if (useTls && isNotBlank(tlsKeystoreFilename)) {
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsKeystoreFilename), KEYSTORE_FILE_IN_CONTAINER);
            commandParts.add("--https-key-store-file=" + KEYSTORE_FILE_IN_CONTAINER);
            commandParts.add("--https-key-store-password=" + tlsKeystorePassword);
        }

        if (providerClassLocation != null) {
            createKeycloakExtensionProvider(providerClassLocation);
        }

        if (providerLibsLocations != null) {
            providerLibsLocations.forEach(file -> withCopyFileToContainer(
                MountableFile.forHostPath(file.getAbsolutePath()), DEFAULT_KEYCLOAK_PROVIDERS_LOCATION + "/"));
        }

        setCommand(commandParts.toArray(new String[0]));
    }

    @Override
    public KeycloakContainer withCommand(String cmd) {
        throw new IllegalStateException("You are trying to set custom container commands, which is currently not supported by this Testcontainer.");
    }

    @Override
    public KeycloakContainer withCommand(String... commandParts) {
        throw new IllegalStateException("You are trying to set custom container commands, which is currently not supported by this Testcontainer.");
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        if (!importFiles.isEmpty()) {
            logger().info("Connect to Keycloak container to import given realm files.");
            Keycloak kcAdmin = getKeycloakAdminClient();
            try {
                for (String importFile : importFiles) {
                    logger().info("Importing realm from file {}", importFile);
                    kcAdmin.realms().create(
                        new ObjectMapper().readValue(this.getClass().getResource(importFile), RealmRepresentation.class)
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
        createKeycloakExtensionDeployment(DEFAULT_KEYCLOAK_PROVIDERS_LOCATION, DEFAULT_KEYCLOAK_PROVIDERS_NAME, extensionClassFolder);
    }

    /**
     * Maps the provided {@code extensionClassFolder} as an exploded extension.jar to the {@code deploymentLocation}.
     *
     * @param deploymentLocation   the target deployments location of the Keycloak server.
     * @param extensionName        the name suffix of the created extension.
     * @param extensionClassFolder a path relative to the current classpath root.
     */
    protected void createKeycloakExtensionDeployment(String deploymentLocation, String extensionName, String extensionClassFolder) {

        requireNonNull(deploymentLocation, "deploymentLocation must not be null");
        requireNonNull(extensionName, "extensionName must not be null");
        requireNonNull(extensionClassFolder, "extensionClassFolder must not be null");

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
                withCopyFileToContainer(MountableFile.forHostPath(file.getAbsolutePath()), deploymentLocation + "/" + extensionName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

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

    public KeycloakContainer withAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
        return self();
    }

    public KeycloakContainer withAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
        return self();
    }

    public KeycloakContainer withContextPath(String contextPath) {
        this.contextPath = contextPath;
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

    public KeycloakContainer withStartupTimeout(Duration startupTimeout) {
        this.startupTimeout = startupTimeout;
        return self();
    }

    public KeycloakContainer useTls() {
        // server.keystore is provided with this testcontainer
        return useTlsKeystore("tls.jks", "changeit");
    }

    public KeycloakContainer useTls(String tlsCertificateFilename, String tlsCertificateKeyFilename) {
        requireNonNull(tlsCertificateFilename, "tlsCertificateFilename must not be null");
        requireNonNull(tlsCertificateKeyFilename, "tlsCertificateKeyFilename must not be null");
        this.tlsCertificateFilename = tlsCertificateFilename;
        this.tlsCertificateKeyFilename = tlsCertificateKeyFilename;
        this.useTls = true;
        return self();
    }

    public KeycloakContainer useTlsKeystore(String tlsKeystoreFilename, String tlsKeystorePassword) {
        requireNonNull(tlsKeystoreFilename, "tlsKeystoreFilename must not be null");
        requireNonNull(tlsKeystorePassword, "tlsKeystorePassword must not be null");
        this.tlsKeystoreFilename = tlsKeystoreFilename;
        this.tlsKeystorePassword = tlsKeystorePassword;
        this.useTls = true;
        return self();
    }

    public KeycloakContainer withFeaturesEnabled(String... features) {
        this.featuresEnabled = features;
        return self();
    }

    public KeycloakContainer withFeaturesDisabled(String... features) {
        this.featuresDisabled = features;
        return self();
    }

    public Keycloak getKeycloakAdminClient() {
        ResteasyClient resteasyClient = new ResteasyClientBuilderImpl()
            .register(JacksonProvider.class, 100)
            .build();
        return KeycloakBuilder.builder()
            .serverUrl(getAuthServerUrl())
            .realm(MASTER_REALM)
            .username(getAdminUsername())
            .password(getAdminPassword())
            .clientId(ADMIN_CLI_CLIENT)
            .resteasyClient(resteasyClient)
            .build();
    }

    public String getAuthServerUrl() {
        return String.format("http%s://%s:%s%s", useTls ? "s" : "", getContainerIpAddress(),
            useTls ? getHttpsPort() : getHttpPort(), getContextPath());
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

    public String getContextPath() {
        return contextPath;
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

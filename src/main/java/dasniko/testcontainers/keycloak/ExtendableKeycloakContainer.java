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

import org.apache.commons.io.FilenameUtils;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.importer.ExplodedImporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jetbrains.annotations.NotNull;
import org.keycloak.admin.client.Keycloak;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.MountableFile;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
@SuppressWarnings({"resource", "unused"})
public abstract class ExtendableKeycloakContainer<SELF extends ExtendableKeycloakContainer<SELF>> extends GenericContainer<SELF> {

    public static final String MASTER_REALM = "master";
    public static final String ADMIN_CLI_CLIENT = "admin-cli";
    public static final WaitStrategy LOG_WAIT_STRATEGY = Wait.forLogMessage(".*Running the server in development mode\\. DO NOT use this configuration in production.*\\n", 1);

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak";
    private static final String KEYCLOAK_VERSION = "nightly";

    private static final int KEYCLOAK_PORT_HTTP = 8080;
    private static final int KEYCLOAK_PORT_HTTPS = 8443;
    private static final int KEYCLOAK_PORT_DEBUG = 8787;
    private static final int KEYCLOAK_PORT_MGMT = 9000;
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final int DEFAULT_INITIAL_RAM_PERCENTAGE = 1;
    private static final int DEFAULT_MAX_RAM_PERCENTAGE = 5;

    private static final String KEYCLOAK_ADMIN_USER = "admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "admin";
    private static final String KEYCLOAK_CONTEXT_PATH = "";
    private static final String KEYCLOAK_HOME_DIR = "/opt/keycloak";
    private static final String KEYCLOAK_CONF_DIR = KEYCLOAK_HOME_DIR + "/conf";

    private static final String DEFAULT_KEYCLOAK_PROVIDERS_NAME = "providers.jar";
    private static final String DEFAULT_KEYCLOAK_PROVIDERS_LOCATION = KEYCLOAK_HOME_DIR + "/providers";
    private static final String DEFAULT_REALM_IMPORT_FILES_LOCATION = KEYCLOAK_HOME_DIR + "/data/import/";

    private static final String KEYSTORE_FILE_IN_CONTAINER = KEYCLOAK_CONF_DIR + "/server.keystore";
    private static final String TRUSTSTORE_FILE_IN_CONTAINER = KEYCLOAK_CONF_DIR + "/server.truststore";

    private String adminUsername = KEYCLOAK_ADMIN_USER;
    private String adminPassword = KEYCLOAK_ADMIN_PASSWORD;
    private String contextPath = KEYCLOAK_CONTEXT_PATH;
    private int initialRamPercentage = DEFAULT_INITIAL_RAM_PERCENTAGE;
    private int maxRamPercentage = DEFAULT_MAX_RAM_PERCENTAGE;

    private final Set<String> importFiles;
    private String tlsCertificateFilename;
    private String tlsCertificateKeyFilename;
    private String tlsKeystoreFilename;
    private String tlsKeystorePassword;
    private String tlsTruststoreFilename;
    private String tlsTruststorePassword;
    private List<String> tlsTrustedCertificateFilenames;
    private boolean useTls = false;
    private boolean disabledCaching = false;
    private boolean metricsEnabled = false;
    private boolean debugEnabled = false;
    private int debugHostPort;
    private boolean debugSuspend = false;
    private HttpsClientAuth httpsClientAuth = HttpsClientAuth.NONE;

    private boolean useVerbose = false;
    private String[] featuresEnabled = null;
    private String[] featuresDisabled = null;

    private Duration startupTimeout = DEFAULT_STARTUP_TIMEOUT;
    private boolean customWaitStrategySet = false;

    private List<String> providerClassLocations;
    private List<File> providerLibsLocations;
    private List<String> customCommandParts;

    /**
     * Create a KeycloakContainer with default image and version tag
     */
    public ExtendableKeycloakContainer() {
        this(KEYCLOAK_IMAGE + ":" + KEYCLOAK_VERSION);
    }

    /**
     * Create a KeycloakContainer by passing the full docker image name
     *
     * @param dockerImageName Full docker image name, e.g. quay.io/keycloak/keycloak:24.0
     */
    public ExtendableKeycloakContainer(String dockerImageName) {
        super(dockerImageName);
        withExposedPorts(KEYCLOAK_PORT_HTTP, KEYCLOAK_PORT_HTTPS, KEYCLOAK_PORT_MGMT);
        importFiles = new HashSet<>();
        withLogConsumer(new Slf4jLogConsumer(logger()));
    }

    @Override
    protected void configure() {
        List<String> commandParts = new ArrayList<>();
        if (useVerbose) {
            commandParts.add("--verbose");
        }
        commandParts.add("start-dev");

        if (!contextPath.equals(KEYCLOAK_CONTEXT_PATH)) {
            withEnv("KC_HTTP_RELATIVE_PATH", contextPath);
        }

        if (featuresEnabled != null) {
            withEnv("KC_FEATURES", String.join(",", featuresEnabled));
        }

        if (featuresDisabled != null) {
            withEnv("KC_FEATURES_DISABLED", String.join(",", featuresDisabled));
        }

        withEnv("KEYCLOAK_ADMIN", adminUsername);
        withEnv("KEYCLOAK_ADMIN_PASSWORD", adminPassword);
        withEnv("JAVA_OPTS_KC_HEAP", "-XX:InitialRAMPercentage=%d -XX:MaxRAMPercentage=%d".formatted(initialRamPercentage, maxRamPercentage));

        if (useTls && isNotBlank(tlsCertificateFilename)) {
            String tlsCertFilePath = KEYCLOAK_CONF_DIR + "/tls.crt";
            String tlsCertKeyFilePath = KEYCLOAK_CONF_DIR + "/tls.key";
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsCertificateFilename), tlsCertFilePath);
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsCertificateKeyFilename), tlsCertKeyFilePath);
            withEnv("KC_HTTPS_CERTIFICATE_FILE", tlsCertFilePath);
            withEnv("KC_HTTPS_CERTIFICATE_KEY_FILE", tlsCertKeyFilePath);
        } else if (useTls && isNotBlank(tlsKeystoreFilename)) {
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsKeystoreFilename), KEYSTORE_FILE_IN_CONTAINER);
            withEnv("KC_HTTPS_KEY_STORE_FILE", KEYSTORE_FILE_IN_CONTAINER);
            withEnv("KC_HTTPS_KEY_STORE_PASSWORD", tlsKeystorePassword);
        }
        if (useTls && isNotBlank(tlsTruststoreFilename)) {
            withCopyFileToContainer(MountableFile.forClasspathResource(tlsTruststoreFilename), TRUSTSTORE_FILE_IN_CONTAINER);
            withEnv("KC_HTTPS_TRUST_STORE_FILE", TRUSTSTORE_FILE_IN_CONTAINER);
            withEnv("KC_HTTPS_TRUST_STORE_PASSWORD", tlsTruststorePassword);
        }
        if (isNotEmpty(tlsTrustedCertificateFilenames)) {
            List<String> truststorePaths = new ArrayList<>();
            tlsTrustedCertificateFilenames.forEach(certificateFilename -> {
                String certPathInContainer = KEYCLOAK_CONF_DIR + (certificateFilename.startsWith("/") ? "" : "/") + certificateFilename;
                withCopyFileToContainer(MountableFile.forClasspathResource(certificateFilename), certPathInContainer);
                truststorePaths.add(certPathInContainer);
            });
            withEnv("KC_TRUSTSTORE_PATHS", String.join(",", truststorePaths));
        }
        withEnv("KC_HTTPS_CLIENT_AUTH", httpsClientAuth.toString());
        withEnv("KC_HTTPS_MANAGEMENT_CLIENT_AUTH", HttpsClientAuth.NONE.toString());

        withEnv("KC_METRICS_ENABLED", Boolean.toString(metricsEnabled));
        withEnv("KC_HEALTH_ENABLED", Boolean.toString(Boolean.TRUE));
        if (!customWaitStrategySet) {
            HttpWaitStrategy waitStrategy = Wait.forHttp(contextPath + "/health/started").forPort(KEYCLOAK_PORT_MGMT);
            if (useTls) {
                waitStrategy = waitStrategy.usingTls().allowInsecure();
            }
            setWaitStrategy(waitStrategy.withStartupTimeout(startupTimeout));
        }

        if (providerClassLocations != null && !providerClassLocations.isEmpty()) {
            AtomicInteger index = new AtomicInteger(0);
            providerClassLocations.forEach(providerClassLocation -> createKeycloakExtensionDeployment(
                DEFAULT_KEYCLOAK_PROVIDERS_LOCATION,
                index.getAndIncrement() + "_" + DEFAULT_KEYCLOAK_PROVIDERS_NAME,
                providerClassLocation
            ));
        }

        if (providerLibsLocations != null) {
            providerLibsLocations.forEach(file -> {
                String containerPath = DEFAULT_KEYCLOAK_PROVIDERS_LOCATION + "/" + file.getName();
                withCopyFileToContainer(MountableFile.forHostPath(file.getAbsolutePath()), containerPath);
            });
        }

        commandParts.add("--import-realm");
        if (!importFiles.isEmpty()) {
            for (String importFile : importFiles) {
                // TODO: a strategy for files with the same name but in the different dirs
                String importFileInContainer = DEFAULT_REALM_IMPORT_FILES_LOCATION + FilenameUtils.getName(importFile);
                withCopyFileToContainer(MountableFile.forClasspathResource(importFile, 0644), importFileInContainer);
            }
        }

        /* caching is disabled per default in dev-mode, thus we overwrite that config, unless #withDisabledCaching() has been called */
        if (!disabledCaching) {
            withEnv("KC_SPI_THEME_CACHE_THEMES", String.valueOf(true));
            withEnv("KC_SPI_THEME_CACHE_TEMPLATES", String.valueOf(true));

            // set the max-age directive for the Cache-Control header to 30 days (Keycloak default)
            withEnv("KC_SPI_THEME_STATIC_MAX_AGE", String.valueOf(2592000));
        }

        if (debugEnabled) {
            withEnv("DEBUG", Boolean.toString(Boolean.TRUE));
            withEnv("DEBUG_PORT", "*:" + KEYCLOAK_PORT_DEBUG);
            if (debugHostPort > 0) {
                addFixedExposedPort(debugHostPort, KEYCLOAK_PORT_DEBUG);
            } else {
                addExposedPort(KEYCLOAK_PORT_DEBUG);
            }
            if (debugSuspend) {
                withEnv("DEBUG_SUSPEND", "y");
            }
        }

        if (customCommandParts != null) {
            logger().warn("You are using custom command parts. " +
                "Container behavior and configuration may be corrupted. " +
                "You are self responsible for proper behavior and functionality!\n" +
                "CustomCommandParts: {}", customCommandParts);
            commandParts.addAll(customCommandParts);
        }

        setCommand(commandParts.toArray(new String[0]));
    }

    @Override
    public SELF withCommand(String cmd) {
        throw new IllegalStateException("You are trying to set custom container commands, which is not supported by this Testcontainer. Try using the withCustomCommand() method.");
    }

    @Override
    public SELF withCommand(String... commandParts) {
        throw new IllegalStateException("You are trying to set custom container commands, which is not supported by this Testcontainer. Try using the withCustomCommand() method.");
    }

    @Override
    public SELF waitingFor(@NotNull WaitStrategy waitStrategy) {
        customWaitStrategySet = true;
        return super.waitingFor(waitStrategy);
    }

    public SELF withCustomCommand(String cmd) {
        if (customCommandParts == null) {
            customCommandParts = new ArrayList<>();
        }
        customCommandParts.add(cmd);
        return self();
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
                file = Files.createTempFile("keycloak", ".jar").toFile();
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

    public SELF withRealmImportFile(String importFile) {
        this.importFiles.add(importFile);
        return self();
    }

    public SELF withRealmImportFiles(String... files) {
        Arrays.stream(files).forEach(this::withRealmImportFile);
        return self();
    }

    public SELF withAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
        return self();
    }

    public SELF withAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
        return self();
    }

    public SELF withContextPath(String contextPath) {
        this.contextPath = contextPath;
        return self();
    }

    public SELF withRamPercentage(int initialRamPercentage, int maxRamPercentage) {
        this.initialRamPercentage = initialRamPercentage;
        this.maxRamPercentage = maxRamPercentage;
        return self();
    }

    /**
     * Exposes the given classes locations as an exploded providers.jar.
     *
     * @param classesLocations classes locations relative to the current classpath root.
     */
    public SELF withProviderClassesFrom(String... classesLocations) {
        this.providerClassLocations = Arrays.asList(classesLocations);
        return self();
    }

    /**
     * Exposes the default classes location <code>target/classes</code> as an exploded providers.jar.
     */
    public SELF withDefaultProviderClasses() {
        return withProviderClassesFrom("target/classes");
    }

    public SELF withProviderLibsFrom(List<File> libs) {
        this.providerLibsLocations = libs;
        return self();
    }

    public SELF withStartupTimeout(Duration startupTimeout) {
        this.startupTimeout = startupTimeout;
        return self();
    }

    public SELF useTls() {
        // server.keystore is provided with this testcontainer
        return useTlsKeystore("tls.jks", "changeit");
    }

    public SELF useTls(String tlsCertificateFilename, String tlsCertificateKeyFilename) {
        requireNonNull(tlsCertificateFilename, "tlsCertificateFilename must not be null");
        requireNonNull(tlsCertificateKeyFilename, "tlsCertificateKeyFilename must not be null");
        this.tlsCertificateFilename = tlsCertificateFilename;
        this.tlsCertificateKeyFilename = tlsCertificateKeyFilename;
        this.useTls = true;
        return self();
    }

    public SELF useTlsKeystore(String tlsKeystoreFilename, String tlsKeystorePassword) {
        requireNonNull(tlsKeystoreFilename, "tlsKeystoreFilename must not be null");
        requireNonNull(tlsKeystorePassword, "tlsKeystorePassword must not be null");
        this.tlsKeystoreFilename = tlsKeystoreFilename;
        this.tlsKeystorePassword = tlsKeystorePassword;
        this.useTls = true;
        return self();
    }

    /**
     * @deprecated Will be removed soon! Use {@link #withTrustedCertificates(List)} and {@link #withHttpsClientAuth(HttpsClientAuth)} instead.
     */
    @Deprecated(forRemoval = true)
    public SELF useMutualTls(String tlsTruststoreFilename, String tlsTruststorePassword, HttpsClientAuth httpsClientAuth) {
        requireNonNull(tlsTruststoreFilename, "tlsTruststoreFilename must not be null");
        requireNonNull(tlsTruststorePassword, "tlsTruststorePassword must not be null");
        requireNonNull(httpsClientAuth, "httpsClientAuth must not be null");
        this.tlsTruststoreFilename = tlsTruststoreFilename;
        this.tlsTruststorePassword = tlsTruststorePassword;
        this.httpsClientAuth = httpsClientAuth;
        this.useTls = true;
        return self();
    }

    /**
     * Configure the Keycloak Truststore to communicate through TLS.
     *
     * @param tlsTrustedCertificateFilenames List of pkcs12 (p12 or pfx file extensions), PEM files, or directories containing those files
     *                                       that will be used as a system truststore.
     * @return self
     */
    public SELF withTrustedCertificates(List<String> tlsTrustedCertificateFilenames) {
        requireNonNull(tlsTrustedCertificateFilenames, "tlsTrustCertificateFilenames must not be null");
        this.tlsTrustedCertificateFilenames = tlsTrustedCertificateFilenames;
        return self();
    }

    /**
     * Configures the server to require/request client authentication.
     *
     * @param httpsClientAuth The http-client-auth mode
     * @return self
     */
    public SELF withHttpsClientAuth(HttpsClientAuth httpsClientAuth) {
        requireNonNull(httpsClientAuth, "httpsClientAuth must not be null");
        this.httpsClientAuth = httpsClientAuth;
        this.useTls = true;
        return self();
    }

    public SELF withVerboseOutput() {
        this.useVerbose = true;
        return self();
    }

    public SELF withFeaturesEnabled(String... features) {
        this.featuresEnabled = features;
        return self();
    }

    public SELF withFeaturesDisabled(String... features) {
        this.featuresDisabled = features;
        return self();
    }

    public SELF withDisabledCaching() {
        this.disabledCaching = true;
        return self();
    }

    public SELF withEnabledMetrics() {
        this.metricsEnabled = true;
        return self();
    }

    /**
     * Enable remote debugging in Keycloak and expose it on a random port.
     */
    public SELF withDebug() {
        return withDebugFixedPort(0, false);
    }

    /**
     * Enable remote debugging in Keycloak and expose it on a fixed port.
     *
     * @param hostPort The port on the host machine
     * @param suspend Control if Keycloak should wait until a debugger is attached
     */
    public SELF withDebugFixedPort(int hostPort, boolean suspend) {
        return withDebug(hostPort, suspend);
    }

    private SELF withDebug(int hostPort, boolean suspend) {
        this.debugEnabled = true;
        this.debugHostPort = hostPort;
        this.debugSuspend = suspend;
        return self();
    }

    public Keycloak getKeycloakAdminClient() {
        if (useTls) {
            return Keycloak.getInstance(getAuthServerUrl(), MASTER_REALM, getAdminUsername(), getAdminPassword(), ADMIN_CLI_CLIENT, buildSslContext());
        } else {
            return Keycloak.getInstance(getAuthServerUrl(), MASTER_REALM, getAdminUsername(), getAdminPassword(), ADMIN_CLI_CLIENT);
        }
    }

    private SSLContext buildSslContext() {
        SSLContext sslContext;
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            if (this.tlsKeystoreFilename != null) {
                keyStore.load(loadResourceAsStream(this.tlsKeystoreFilename), this.tlsKeystorePassword.toCharArray());
            } else if (this.tlsCertificateFilename != null) {
                keyStore.load(null);
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(loadResourceAsStream(this.tlsCertificateFilename));
                keyStore.setCertificateEntry(certificate.getSubjectX500Principal().getName(), certificate);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
        } catch (GeneralSecurityException | IOException e) {
            sslContext = null;
        }
        return sslContext;
    }

    private InputStream loadResourceAsStream(String filename) {
        return ExtendableKeycloakContainer.class.getClassLoader().getResourceAsStream(filename);
    }

    public String getProtocol() {
        return "http%s".formatted(useTls ? "s": "");
    }

    public String getAuthServerUrl() {
        return String.format("%s://%s:%s%s", getProtocol(), getHost(), useTls ? getHttpsPort() : getHttpPort(), getContextPath());
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

    public int getHttpMgmtPort() {
        return getMappedPort(KEYCLOAK_PORT_MGMT);
    }

    /**
     * Get the mapped port for remote debugging. Should only be used if debugging has been enabled.
     * @return the mapped port or <code>-1</code> if debugging has not been configured
     * @see #withDebug()
     * @see #withDebugFixedPort(int, boolean)
     */
    public int getDebugPort() {
        return debugEnabled ? getMappedPort(KEYCLOAK_PORT_DEBUG) : -1;
    }

    public String getContextPath() {
        return contextPath;
    }

    public Duration getStartupTimeout() {
        return startupTimeout;
    }

    @SuppressWarnings({"ConstantValue", "UnreachableCode"})
    public String getKeycloakDefaultVersion() {
        return KEYCLOAK_VERSION.equals("nightly") ? "999.0.0-SNAPSHOT" : KEYCLOAK_VERSION;
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private boolean isNotEmpty(List<String> l) {
        return l != null && !l.isEmpty();
    }

}

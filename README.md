# Keycloak Testcontainer

Spin up a real [Keycloak](https://www.keycloak.org/) OAuth2/OIDC identity provider as a Docker container in your Java integration tests — no mocks, no manual setup.
Built on [Testcontainers](https://www.testcontainers.org/), it works with JUnit 5 and integrates seamlessly with Spring Boot, Quarkus, and any other Java framework.
**New here? → [Quick Start](docs/quickstart.md)**

[![GitHub Release](https://img.shields.io/github/v/release/dasniko/testcontainers-keycloak?label=Release)](https://github.com/dasniko/testcontainers-keycloak/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.dasniko/testcontainers-keycloak.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.github.dasniko/testcontainers-keycloak)
![GitHub Release Date](https://img.shields.io/github/release-date-pre/dasniko/testcontainers-keycloak)
![Github Last Commit](https://img.shields.io/github/last-commit/dasniko/testcontainers-keycloak)
![License](https://img.shields.io/github/license/dasniko/testcontainers-keycloak?label=License)

[![Keycloak Version](https://img.shields.io/badge/Keycloak-26.5-blue)](https://www.keycloak.org)
![Java Version](https://img.shields.io/badge/Java-11-f89820)
[![GitHub Stars](https://img.shields.io/github/stars/dasniko/testcontainers-keycloak)](https://github.com/dasniko/testcontainers-keycloak/stargazers)
[![CI build](https://github.com/dasniko/testcontainers-keycloak/actions/workflows/maven.yml/badge.svg)](https://github.com/dasniko/testcontainers-keycloak/actions/workflows/maven.yml)

## Setup

The release versions of this project are available at [Maven Central](https://central.sonatype.com/artifact/com.github.dasniko/testcontainers-keycloak).

**Maven:**
```xml
<dependency>
  <groupId>com.github.dasniko</groupId>
  <artifactId>testcontainers-keycloak</artifactId>
  <version>VERSION</version>
  <scope>test</scope>
</dependency>
```

**Gradle (Kotlin DSL):**
```kotlin
testImplementation("com.github.dasniko:testcontainers-keycloak:VERSION")
```

> [!TIP]
> There is also a `999.0.0-SNAPSHOT` version available, pointing to the `nightly` Docker image by default and using the `999.0.0-SNAPSHOT` Keycloak libraries as dependencies.

## Version Compatibility

> [!IMPORTANT]
> See [version overview](docs/versions.md) for an overview of which Keycloak release works with this library by default and which [Testcontainers](https://www.testcontainers.org/) version is used.
> This library is, like Keycloak, only developed in the forward direction — no LTS, no backports. Make sure to stay up to date.

## Contents

- [How to use](#how-to-use)
  - [Default _(deprecated)_](#default-deprecated)
  - [Custom image](#custom-image)
  - [Initial admin user credentials](#initial-admin-user-credentials)
  - [Realm Import](#realm-import)
  - [Getting an admin client and other information](#getting-an-admin-client-and-other-information-from-the-testcontainer)
  - [Context Path](#context-path)
  - [Management Port](#management-port)
  - [Memory Settings](#memory-settings)
- [TLS (SSL) Usage](#tls-ssl-usage)
  - [Built-in TLS Keystore](#built-in-tls-keystore)
  - [Custom TLS Cert and Key](#custom-tls-cert-and-key)
  - [Custom TLS Keystore](#custom-tls-keystore)
- [Keycloak Feature Flags](#keycloak-feature-flags)
- [Custom CLI Config arguments](#custom-cli-config-arguments)
- [Starting in production mode](#starting-in-production-mode)
  - [Optimized flag](#optimized-flag)
- [Testing Custom Extensions](#testing-custom-extensions)
  - [Dependencies & 3rd-party Libraries](#dependencies--3rd-party-libraries)
  - [Extending KeycloakContainer](#extending-keycloakcontainer)
  - [Remote Debugger Support](#remote-debugger-support)
- [Usage in your application framework tests](#usage-in-your-application-framework-tests)
  - [Spring Boot](#spring-boot)
  - [Quarkus](#quarkus)
- [YouTube Videos](#youtube-videos-about-keycloak-testcontainers)

## How to use

_The `@Container` annotation used here in the readme is from the JUnit 5 support of Testcontainers.
Please refer to the Testcontainers documentation for more information._

### Default _(deprecated)_

> [!IMPORTANT]
> Starting with version 4.2, the default constructor is deprecated and should no longer be used.
> Please use the `new KeycloakContainer(String imageName)` constructor instead (see section [Custom image](#custom-image) below).
> The behavior of the default constructor will most likely change in future versions!

Simply spin up a default Keycloak instance:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer();
```

### Custom image

Use a distinct Keycloak Docker image/version:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4");
```

### Initial admin user credentials

Use different admin credentials than the default internal (`admin`/`admin`) ones:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withAdminUsername("myKeycloakAdminUser")
    .withAdminPassword("tops3cr3t");
```

### Realm Import

Power up a Keycloak instance with one or more existing realm JSON config files (from classpath):

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withRealmImportFile("/test-realm.json");
```

or

```java
    .withRealmImportFiles("/test-realm-1.json", "/test-realm-2.json");
```

If your realm JSON configuration file includes user definitions - particularly the admin user
for the master realm - ensure you disable the automatic bootstrapping of the admin user:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withBootstrapAdminDisabled()
    .withRealmImportFile("/test-realm.json");
```

To retrieve a working Keycloak Admin Client from the container, make sure to override the admin
credentials to match those in your imported realm JSON configuration file:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withBootstrapAdminDisabled()
    .withRealmImportFile("/test-realm.json")
    .withAdminUsername("myKeycloakAdminUser")
    .withAdminPassword("tops3cr3t");
```

### Getting an admin client and other information from the testcontainer

You can get an instance of `org.keycloak.admin.Keycloak` admin client directly from the container, using

```java
org.keycloak.admin.Keycloak keycloakAdmin = keycloakContainer.getKeycloakAdminClient();
```
The admin client is configured with current admin credentials.

> [!NOTE]
> The `org.keycloak:keycloak-admin-client` package is a transitive dependency of this project, ready to be used by you in your tests, no need to add it on your own.

You can also get several properties from the Keycloak container:

```java
String authServerUrl = keycloak.getAuthServerUrl();
String adminUsername = keycloak.getAdminUsername();
String adminPassword = keycloak.getAdminPassword();
```

with these properties, you can create e.g. a custom `org.keycloak.admin.client.Keycloak` object to connect to the container and do optional further configuration:

```java
Keycloak keycloakAdminClient = KeycloakBuilder.builder()
    .serverUrl(keycloak.getAuthServerUrl())
    .realm(KeycloakContainer.MASTER_REALM)
    .clientId(KeycloakContainer.ADMIN_CLI_CLIENT)
    .username(keycloak.getAdminUsername())
    .password(keycloak.getAdminPassword())
    .build();
```

### Context Path

As Keycloak comes with the default context path `/`, you can set your custom context path, e.g. for compatibility reasons to previous versions, with:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withContextPath("/auth");
```

### Management Port

Starting from Keycloak version 25.0.0, Keycloak will propagate `/health` and `/metrics` on "Management Port", see [Configuring the Management Interface](https://www.keycloak.org/server/management-interface) and [Migration Guide](https://www.keycloak.org/docs/latest/upgrading/index.html#management-port-for-metrics-and-health-endpoints)

```java
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag").withEnabledMetrics();
keycloak.start();
keycloak.getMgmtServerUrl();
```

### Memory Settings

As of Keycloak 24 the container doesn't use an absolute amount of memory, but a relative percentage of the overall available memory to the container, [see also here](https://www.keycloak.org/server/containers#_specifying_different_memory_settings).

This testcontainer has an initial memory setting of

    JAVA_OPTS_KC_HEAP="-XX:InitialRAMPercentage=1 -XX:MaxRAMPercentage=5"

to not overload your environment.
You can override this setting with the `withRamPercentage(initial, max)` method:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withRamPercentage(50, 70);
```

## TLS (SSL) Usage

You have three options to use HTTPS/TLS secured communication with your Keycloak Testcontainer.

### Built-in TLS Keystore

This Keycloak Testcontainer comes with built-in TLS certificate (`tls.crt`), key (`tls.key`) and Java KeyStore (`tls.jks`) files, located in the `resources` folder.
You can use this configuration by only configuring your testcontainer like this:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag").useTls();
```

The password for the provided Java KeyStore file is `changeit`.
See also [`KeycloakContainerHttpsTest.shouldStartKeycloakWithProvidedTlsKeystore`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerHttpsTest.java#L39).

The method `getAuthServerUrl()` will then return the HTTPS url.

### Custom TLS Cert and Key

Of course you can also provide your own certificate and key file for usage in this Testcontainer:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .useTls("your_custom.crt", "your_custom.key");
```

See also [`KeycloakContainerHttpsTest.shouldStartKeycloakWithCustomTlsCertAndKey`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerHttpsTest.java#L47).

The method `getAuthServerUrl()` will also return the HTTPS url.

### Custom TLS Keystore

Last but not least, you can also provide your own keystore file for usage in this Testcontainer:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .useTlsKeystore("your_custom.jks", "password_for_your_custom_keystore");
```

See also [`KeycloakContainerHttpsTest.shouldStartKeycloakWithCustomTlsKeystore`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerHttpsTest.java#L55).

The method `getAuthServerUrl()` will also return the HTTPS url.

## Keycloak Feature Flags

You can enable and disable Keycloak feature flags on your Testcontainer:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withFeaturesEnabled("docker", "scripts", "...")
    .withFeaturesDisabled("authorization", "impersonation", "...");
```

## Custom CLI Config arguments

All default configurations in this Testcontainer is done through environment variables.
You can overwrite and/or add config settings on command-line-level (cli args) with this method:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withCustomCommand("--hostname=keycloak.local");
```

A warning will be printed to the log output when custom command parts are being used, so that you are aware that you are responsible on your own for proper execution of this container.

## Starting in production mode

By default, the container is started in dev mode (`start-dev`).
If needed you can enable production mode:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withProductionMode();
```

### Optimized flag

It is possible that you use your own pre-build image with the `--optimized` flag.
Setting this option will implicitly enable production mode!

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("<YOUR_IMAGE>" + ":<YOUR_TAG>")
    .withOptimizedFlag();
```

> [!NOTE]
> If you don't enable the health endpoint in your custom image, the container will not be healthy.
> In this case please provide your own waitStrategy.

Check out the tests at [`KeycloakContainerOptimizedTest`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerOptimizedTest.java).

## Testing Custom Extensions

To ease extension testing, you can tell the Keycloak Testcontainer to detect extensions in a given classpath folder.
This allows to test extensions directly in the same module without a packaging step.

If you have your Keycloak extension code in the `src/main/java` folder, then the resulting classes will be generated to the `target/classes` folder.
To test your extensions you just need to tell `KeycloakContainer` to consider extensions from the `target/classes` folder.

Keycloak Testcontainer will then dynamically generate a packaged jar file with the extension code that is then picked up by Keycloak.

```java
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withProviderClassesFrom("target/classes");
```

For your convenience, there's now (since 3.3) a default method, which yields to `target/classes` internally:

```java
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withDefaultProviderClasses();
```

See also [`KeycloakContainerExtensionTest`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerExtensionTest.java) class.

### Dependencies & 3rd-party Libraries

If you need to provide any 3rd-party dependency or library, you can do this with

```java
List<File> libs = ...;
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withProviderLibsFrom(libs);
```

You have to provide a list of resolvable `File`s.

> [!TIP]
> If you want/need to use dependencies from e.g., Maven (or Gradle), you can use [ShrinkWrap Resolvers](https://github.com/shrinkwrap/resolver).
> See, as an example, how this is used at the [`KeycloakContainerExtensionTest#shouldDeployProviderWithDependencyAndCallCustomEndpoint()`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerExtensionTest.java) test.

### Extending KeycloakContainer

In case you need a custom implementation of the default `KeycloakContainer`, you should inherit from `ExtendableKeycloakContainer`. This allows to set the generics and use your custom implementation without the need for type casts.

```java
public class MyCustomKeycloakContainer extends ExtendableKeycloakContainer<MyCustomKeycloakContainer> {
  public MyCustomKeycloakContainer() {
    super("kcImageName:tag");
  }
  public MyCustomKeycloakContainer(String dockerImageName) {
    super(dockerImageName);
  }
}

...

MyCustomKeycloakContainer keycloakContainer = new MyCustomKeycloakContainer()
    .withAdminPassword("password");
```

### Remote Debugger Support

You can tell the Keycloak Testcontainer to open a debug port for attaching a remote debugger.

This command will enable remote debugging in Keycloak and expose the used debug port in the container on a random port to the outside:

```java
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withDebug();
```

If you want to enable remote debugging on a fixed port and optionally have Keycloak wait (suspend) until a debugger has attached to this port, use this command:

```java
KeycloakContainer keycloak = new KeycloakContainer("kcImageName:tag")
    .withDebugFixedPort(int hostPort, boolean suspend);
```

## Usage in your application framework tests

A common question is how to configure your test setup when you're used to specifying fixed ports in properties or YAML files. With Testcontainers you don't need fixed ports — each framework provides a way to dynamically configure your application context after the container starts.

### Spring Boot

The recommended approach is `@DynamicPropertySource`, which injects the container's dynamic URLs into the Spring `Environment` before the application context starts. For example:

```java
@Testcontainers
@SpringBootTest
class MyTest {

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4")
        .withRealmImportFile("/test-realm.json");

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> keycloak.getAuthServerUrl() + "/realms/test");
    }
}
```

**→ [Full Spring Boot integration guide](docs/spring-boot.md)** — covers OAuth2 resource server, OAuth2 client, shared container patterns, token acquisition, and TLS.

### Quarkus

Implement `QuarkusTestResourceLifecycleManager` to start the container and inject config properties before the Quarkus application context boots:

```java
public class KeycloakTestResource implements QuarkusTestResourceLifecycleManager {

    private static final KeycloakContainer keycloak =
        new KeycloakContainer("quay.io/keycloak/keycloak:26.4")
            .withRealmImportFile("/test-realm.json");

    @Override
    public Map<String, String> start() {
        keycloak.start();
        return Map.of(
            "quarkus.oidc.auth-server-url", keycloak.getAuthServerUrl() + "/realms/test",
            "quarkus.oidc.client-id", "my-client"
        );
    }

    @Override
    public void stop() { keycloak.stop(); }
}
```

**→ [Full Quarkus integration guide](docs/quarkus.md)** — covers OIDC resource server, OIDC client, multi-tenant setups, container injection into tests, and TLS.

### Others

Consult the docs of your application framework on how to dynamically configure your stack for testing.

## YouTube Videos about Keycloak Testcontainers

| [![Integration Tests with Keycloak & Testcontainers](https://img.youtube.com/vi/FEbIW23RoXk/mqdefault.jpg)](https://www.youtube.com/watch?v=FEbIW23RoXk) | [![Keycloak DevDay 2024: Extensions Development with Testcontainers-Keycloak](https://img.youtube.com/vi/l2Lk2Z9mHBs/mqdefault.jpg)](https://www.youtube.com/watch?v=l2Lk2Z9mHBs) | [![Testing Keycloak extensions using Testcontainers](https://img.youtube.com/vi/lBC51XZUM90/mqdefault.jpg)](https://www.youtube.com/watch?v=lBC51XZUM90) |
|---|---|---|
| Integration Tests with Keycloak & Testcontainers | Keycloak DevDay 2024: Extensions Development with Testcontainers-Keycloak | Testing Keycloak extensions using Testcontainers (Keycloak Hour of Code) |

## Credits

Many thanks to the creators and maintainers of [Testcontainers](https://www.testcontainers.org/).
You do an awesome job!

Same goes to the whole [Keycloak](https://www.keycloak.org/) team!

Kudos to [@thomasdarimont](https://github.com/thomasdarimont) for some inspiration for this project.

## License

Apache License 2.0

Copyright (c) 2019-2026 Niko Köbler

See [LICENSE](LICENSE) file for details.

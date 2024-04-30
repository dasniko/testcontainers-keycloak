# Keycloak Testcontainer

A [Testcontainers](https://www.testcontainers.org/) implementation for [Keycloak](https://www.keycloak.org/) SSO.

[![CI build](https://github.com/dasniko/testcontainers-keycloak/actions/workflows/maven.yml/badge.svg)](https://github.com/dasniko/testcontainers-keycloak/actions/workflows/maven.yml)
![](https://img.shields.io/github/v/release/dasniko/testcontainers-keycloak?label=Release)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.dasniko/testcontainers-keycloak.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.github.dasniko%22%20AND%20a:%22testcontainers-keycloak%22)
![](https://img.shields.io/github/license/dasniko/testcontainers-keycloak?label=License)
![](https://img.shields.io/badge/Keycloak-24.0-blue)

## IMPORTANT!!!

> This version only handles Keycloak from version **22.x** and up, as there are major changes coming with this release.
> See also the [blog post](https://www.keycloak.org/2023/07/keycloak-2200-released.html).  
> For older Keycloak versions (until 21.x), see [version 2.x branch](https://github.com/dasniko/testcontainers-keycloak/tree/v2).

## How to use

_The `@Container` annotation used here in the readme is from the JUnit 5 support of Testcontainers.
Please refer to the Testcontainers documentation for more information._

### Default

Simply spin up a default Keycloak instance:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer();
```

### Custom image

Use another Keycloak Docker image/version than used in this Testcontainer:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:24.0");
```

### Realm Import

Power up a Keycloak instance with one or more existing realm JSON config files (from classpath):

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer()
    .withRealmImportFile("/test-realm.json");
```
or
```java
    .withRealmImportFiles("/test-realm-1.json", "/test-realm-2.json");
```

### Initial admin user credentials

Use different admin credentials than the defaut internal (`admin`/`admin`) ones:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer()
    .withAdminUsername("myKeycloakAdminUser")
    .withAdminPassword("tops3cr3t");
```

### Getting an admin client and other information from the testcontainer

You can get an instance of `org.keycloak.admin.Keycloak` admin client directly from the container, using

```java
org.keycloak.admin.Keycloak keycloakAdmin = keycloakContainer.getKeycloakAdminClient();
```
The admin client is configured with current admin credentials.

> The `org.keycloak:keycloak-admin-client` package is now a transitive dependency of this project, ready to be used by you in your tests, no more need to add it on your own.

You can also obtain several properties from the Keycloak container:

```java
String authServerUrl = keycloak.getAuthServerUrl();
String adminUsername = keycloak.getAdminUsername();
String adminPassword = keycloak.getAdminPassword();
```

with these properties, you can create e.g. a custom `org.keycloak.admin.client.Keycloak` object to connect to the container and do optional further configuration:

```java
import dasniko.testcontainers.keycloak.KeycloakContainer;

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
KeycloakContainer keycloak = new KeycloakContainer()
    .withContextPath("/auth");
```

### Memory Settings

As of Keycloak 24 the container doesn't use an absolute amount of memory, but a relative percentage of the overall available memory to the container, [see also here](https://www.keycloak.org/server/containers#_specifying_different_memory_settings).

This testcontainer has an initial memory setting of

    JAVA_OPTS_KC_HEAP="-XX:InitialRAMPercentage=1 -XX:MaxRAMPercentage=5"

to not overload your environment.
You can override this settng with the `withRamPercentage(initial, max)` method:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer()
    .withRamPercentage(50, 70);
```

## TLS (SSL) Usage

You have three options to use HTTPS/TLS secured communication with your Keycloak Testcontainer.

### Built-in TLS Keystore

This Keycloak Testcontainer comes with built-in TLS certificate (`tls.crt`), key (`tls.key`) and Java KeyStore (`tls.jks`) files, located in the `resources` folder.
You can use this configuration by only configuring your testcontainer like this:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer().useTls();
```

The password for the provided Java KeyStore file is `changeit`.
See also [`KeycloakContainerHttpsTest.shouldStartKeycloakWithProvidedTlsKeystore`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerHttpsTest.java#L39).

The method `getAuthServerUrl()` will then return the HTTPS url.

### Custom TLS Cert and Key

Of course you can also provide your own certificate and key file for usage in this Testcontainer:

```java
@Container
private KeycloakContainer keycloak = new KeycloakContainer()
.useTls("your_custom.crt", "your_custom.key");
```

See also [`KeycloakContainerHttpsTest.shouldStartKeycloakWithCustomTlsCertAndKey`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerHttpsTest.java#L47).

The method getAuthServerUrl() will also return the HTTPS url.

### Custom TLS Keystore

Last but not least, you can also provide your own keystore file for usage in this Testcontainer:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer()
    .useTlsKeystore("your_custom.jks", "password_for_your_custom_keystore");
```

See also [`KeycloakContainerHttpsTest.shouldStartKeycloakWithCustomTlsKeystore`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerHttpsTest.java#L55).

The method `getAuthServerUrl()` will also return the HTTPS url.

## Features

You can enable and disable features on your Testcontainer:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer()
    .withFeaturesEnabled("docker", "scripts", "...")
    .withFeaturesDisabled("authorization", "impersonation", "...");
```

## Custom CLI Config arguments

All default configurations in this Testcontainer is done through environment variables.
You can overwrite and/or add config settings on command-line-level (cli args) with this method:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer()
    .withCustomCommand("--hostname=keycloak.local");
```

A warning will be printed to the log output when custom command parts are being used, so that you are aware that you are responsible on your own for proper execution of this container.

## Testing Custom Extensions

To ease extension testing, you can tell the Keycloak Testcontainer to detect extensions in a given classpath folder.
This allows to test extensions directly in the same module without a packaging step.

If you have your Keycloak extension code in the `src/main/java` folder, then the resulting classes will be generated to the `target/classes` folder.
To test your extensions you just need to tell `KeycloakContainer` to consider extensions from the `target/classes` folder.

Keycloak Testcontainer will then dynamically generate a packaged jar file with the extension code that is then picked up by Keycloak.

```java
KeycloakContainer keycloak = new KeycloakContainer()
    .withProviderClassesFrom("target/classes");
```

For your convenience, there's now (since 3.3) a default method, which yields to `target/classes` internally:

```java
KeycloakContainer keycloak = new KeycloakContainer()
    .withDefaultProviderClasses();
```

See also [`KeycloakContainerExtensionTest`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerExtensionTest.java) class.

### Dependencies & 3rd-party Libraries

If you need to provide any 3rd-party dependency or library, you can do this with

```java
List<File> libs = ...;
KeycloakContainer keycloak = new KeycloakContainer()
    .withProviderLibsFrom(libs);
```

You have to provide a list of resolvable `File`s.

### Extending KeycloakContainer

In case you need a custom implementation of the default `KeycloakContainer`, you should inherit from `ExtendableKeycloakContainer`. This allows to set the generics and use your custom implementation without the need for type casts.  

```java
public class MyCustomKeycloakContainer extends ExtendableKeycloakContainer<MyCustomKeycloakContainer> {

	public MyCustomKeycloakContainer() {
		super();
	}

	public MyCustomKeycloakContainer(String dockerImageName) {
		super(dockerImageName);
	}
	
}

...

MyCustomKeycloakContainer keycloakContainer = new MyCustomKeycloakContainer()
    .withAdminPassword("password");
```

#### TIPP

If you want/need to use dependencies from e.g. Maven (or Gradle), you can use [ShrinkWrap Resolvers](https://github.com/shrinkwrap/resolver).
See, as an example, how this is used at the [`KeycloakContainerExtensionTest#shouldDeployProviderWithDependencyAndCallCustomEndpoint()`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerExtensionTest.java) test.

### Remote Debugger Support

You can tell the Keycloak Testcontainer to open a debug port for attaching a remote debugger.

This command will enable remote debugging in Keycloak and expose the used debug port in the container on a random port to the outside:

```java
KeycloakContainer keycloak = new KeycloakContainer()
    .withDebug();
```

If you want to enable remote debugging on a fixed port and optionally have Keycloak wait (suspend) until a debugger has attached to this port, use this command:

```java
KeycloakContainer keycloak = new KeycloakContainer()
    .withDebugFixedPort(int hostPort, boolean suspend);
```

## Setup

The release versions of this project are available at [Maven Central](https://search.maven.org/artifact/com.github.dasniko/testcontainers-keycloak).
Simply put the dependency coordinates to your `pom.xml` (or something similar, if you use e.g. Gradle or something else):

```xml
<dependency>
  <groupId>com.github.dasniko</groupId>
  <artifactId>testcontainers-keycloak</artifactId>
  <version>VERSION</version>
  <scope>test</scope>
</dependency>
```

### JUnit4 Dependency

The testcontainers project itself has a dependency on JUnit4 although it is not needed for this project in order to run (see this [issue](https://github.com/testcontainers/testcontainers-java/issues/970) for more details).
To avoid pulling in JUnit4 this project comes with a dependency on the `quarkus-junit4-mock` library which includes all needed classes as empty stubs. If you need JUnit4 in your project you should exclude this mock library
when declaring the dependency to `testcontainers-keycloak` to avoid issues. Example for maven:

```xml
<dependency>
    <!-- ... see above -->
    <exclusions>
        <exclusion>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit4-mock</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

## Usage in your application framework tests

> This info is not specific to the Keycloak Testcontainer, but using Testcontainers generally.

I mention it here, as I see people asking again and again on how to use it in their test setup, when they think they need to specify a fixed port in their properties or YAML files...  
You don't have to!  
But you have to read the Testcontainers docs and the docs of your application framework on testing resources!!

### Spring (Boot)

Dynamic context configuration with context initializers is your friend.
In particular, look for `@ContextConfiguration` and `ApplicationContextInitializer<ConfigurableApplicationContext>`:
* https://docs.spring.io/spring-framework/docs/current/reference/html/testing.html#spring-testing-annotation-contextconfiguration
* https://docs.spring.io/spring-framework/docs/current/reference/html/testing.html#testcontext-ctx-management-initializers

### Quarkus

Read the docs about the Quarkus Test Resources and use `@QuarkusTestResource` with `QuarkusTestResourceLifecycleManager`
* https://quarkus.io/guides/getting-started-testing#quarkus-test-resource

### Others

Consult the docs of your application framework testing capabilities on how to dynamically configure your stack for testing!

## YouTube Video about Keycloak Testcontainers

[![](http://img.youtube.com/vi/FEbIW23RoXk/maxresdefault.jpg)](http://www.youtube.com/watch?v=FEbIW23RoXk "")


## Testcontainers & Keycloak version compatiblity

For Keycloak versions until 21.x, see [version 2.x branch](https://github.com/dasniko/testcontainers-keycloak/tree/v2)

| Testcontainers-Keycloak | Testcontainers | Keycloak |
|-------------------------|----------------|----------|
| 3.0.0                   | 1.18.3         | 22.0     |
| 3.1.0                   | 1.18.3         | 22.0.5   |
| 3.2.0                   | 1.19.3         | 23.0     |
| 3.3.0                   | 1.19.6         | 24.0     |
| 3.3.1                   | 1.19.6         | 24.0     |

_There might also be other possible version configurations which will work._

See also the [Releases](https://github.com/dasniko/testcontainers-keycloak/releases) page for version and feature update notes.

## Credits

Many thanks to the creators and maintainers of [Testcontainers](https://www.testcontainers.org/).
You do an awesome job!

Same goes to the whole [Keycloak](https://www.keycloak.org/) team!

Kudos to [@thomasdarimont](https://github.com/thomasdarimont) for some inspiration for this project.

## License

Apache License 2.0

Copyright (c) 2019-2023 Niko Köbler

See [LICENSE](LICENSE) file for details.

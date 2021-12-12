# Keycloak Testcontainer

A [Testcontainers](https://www.testcontainers.org/) implementation for [Keycloak](https://www.keycloak.org/) SSO.

[![CI build](https://github.com/dasniko/testcontainers-keycloak/actions/workflows/maven.yml/badge.svg)](https://github.com/dasniko/testcontainers-keycloak/actions/workflows/maven.yml)
![](https://img.shields.io/github/v/release/dasniko/testcontainers-keycloak?label=Release)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.dasniko/testcontainers-keycloak.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.github.dasniko%22%20AND%20a:%22testcontainers-keycloak%22)
![](https://img.shields.io/github/license/dasniko/testcontainers-keycloak?label=License)
![](https://img.shields.io/badge/Keycloak.X-15.1.0-blue)

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
KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak-x:15.0.2");
```

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
Keycloak keycloakAdminClient = KeycloakBuilder.builder()
    .serverUrl(keycloak.getAuthServerUrl())
    .realm("master")
    .clientId("admin-cli")
    .username(keycloak.getAdminUsername())
    .password(keycloak.getAdminPassword())
    .build();
```

## TLS (SSL) Usage

You have two options to use HTTPS/TLS secured communication with your Keycloak Testcontainer.

### Built-in TLS Keystore

This Keycloak Testcontainer comes with built-in TLS certificate (`tls.crt`), key (`tls.key`) and Java KeyStore (`tls.jks`) files, located in the `resources` folder.
You can use this configuration by only configuring your testcontainer like this:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer().useTls();
```

The password for the provided Java KeyStore file is `changeit`.
See also [`KeycloakContainerHttpsTest.shouldStartKeycloakWithProvidedTlsKeystore`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerHttpsTest.java#L38).

The method `getAuthServerUrl()` will then return the HTTPS url.

### Custom TLS Keystore

Of course you can also provide your own keystore file for usage in this Testcontainer:

```java
@Container
KeycloakContainer keycloak = new KeycloakContainer()
    .useTls("your_custom.jks", "password_for_your_custom_keystore");
```

See also [`KeycloakContainerHttpsTest.shouldStartKeycloakWithCustomTlsKeystore`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerHttpsTest.java#L46).

The method `getAuthServerUrl()` will also return the HTTPS url.

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

See also [`KeycloakContainerExtensionTest`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerExtensionTest.java) class.

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


[![](http://img.youtube.com/vi/FEbIW23RoXk/maxresdefault.jpg)](http://www.youtube.com/watch?v=FEbIW23RoXk "")


## Testcontainers & Keycloak-X version compatiblity

For the legacy Keycloak (before `-X`/Quarkus-based distro), see [version 1.x branch](https://github.com/dasniko/testcontainers-keycloak/tree/v1)

|Testcontainers-Keycloak |Testcontainers |Keycloak-X
|---|---|---
|2.0.0-SNAPSHOT |1.16.2 |15.1.0

_There might also be other possible version configurations which will work._

See also the [Releases](https://github.com/dasniko/testcontainers-keycloak/releases) page for version and feature update notes.

## Credits

Many thanks to the creators and maintainers of [Testcontainers](https://www.testcontainers.org/).
You do an awesome job!

Same goes to the whole [Keycloak](https://www.keycloak.org/) team!

Kudos to [@thomasdarimont](https://github.com/thomasdarimont) for some inspiration for this project.

## License

Apache License 2.0

Copyright (c) 2019-2021 Niko Köbler

See [LICENSE](LICENSE) file for details.

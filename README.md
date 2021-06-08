# Keycloak Testcontainer

A [Testcontainers](https://www.testcontainers.org/) implementation for [Keycloak](https://www.keycloak.org/) SSO.

[![CI build](https://github.com/dasniko/testcontainers-keycloak/actions/workflows/maven.yml/badge.svg)](https://github.com/dasniko/testcontainers-keycloak/actions/workflows/maven.yml)
![](https://img.shields.io/github/v/release/dasniko/testcontainers-keycloak?label=Release)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.dasniko/testcontainers-keycloak.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.github.dasniko%22%20AND%20a:%22testcontainers-keycloak%22)
![](https://img.shields.io/github/license/dasniko/testcontainers-keycloak?label=License)
![](https://img.shields.io/badge/Keycloak-13.0.1-blue)

## How to use

_The `@Container` annotation used here in the readme is from the JUnit 5 support of Testcontainers.
Please refer to the Testcontainers documentation for more information._

Simply spin up a default Keycloak instance:

```java
@Container
private KeycloakContainer keycloak = new KeycloakContainer();
```

Use another Keycloak Docker image/version than used in this Testcontainer:

```java
@Container
private KeycloakContainer keycloak = new KeycloakContainer("jboss/keycloak:13.0.1");
```

Power up a Keycloak instance with an existing realm JSON config file (from classpath):

```java
@Container
private KeycloakContainer keycloak = new KeycloakContainer()
    .withRealmImportFile("test-realm.json");
```

Use different admin credentials than the defaut internal (`admin`/`admin`) ones:

```java
@Container
private KeycloakContainer keycloak = new KeycloakContainer()
    .withAdminUsername("myKeycloakAdminUser")
    .withAdminPassword("tops3cr3t");
```

You can obtain several properties form the Keycloak container:

```java
String authServerUrl = keycloak.getAuthServerUrl();
String adminUsername = keycloak.getAdminUsername();
String adminPassword = keycloak.getAdminPassword();
```

with these properties, you can create a `org.keycloak.admin.client.Keycloak` (Keycloak admin client, 3rd party dependency from Keycloak project) object to connect to the container and do optional further configuration:

```java
Keycloak keycloakAdminClient = KeycloakBuilder.builder()
    .serverUrl(keycloak.getAuthServerUrl())
    .realm("master")
    .clientId("admin-cli")
    .username(keycloak.getAdminUsername())
    .password(keycloak.getAdminPassword())
    .build();
```

See also [`KeycloakContainerTest`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerTest.java) class.

## TLS (SSL) Usage

You have several options to use HTTPS/TLS secured communication with your Keycloak Testcontainer.

### Default Support

Plain Keycloak comes with a default Java KeyStore (JKS) with an auto-generated, self-signed certificate on first use.
You can use this TLS secured connection, although your testcontainer doesn't know of anything TLS-related and returns the HTTP-only url with `getAuthServerUrl()`.
In this case, you have to build the auth-server-url on your own, e.g. like this:

```java
String authServerUrl = "https://localhost:" + keycloak.getHttpsPort() + "/auth";
```

See also [`KeycloakContainerHttpsTest.shouldStartKeycloakWithDefaultTlsSupport`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerHttpsTest.java#L23).

### Built-in TLS Cert and Key

This Keycloak Testcontainer comes with built-in TLS certificate (`tls.crt`), key (`tls.key`) and Java KeyStore (`tls.jks`) files, located in the `resources` folder.
You can use this configuration by only configuring your testcontainer like this:

```java
@Container
private KeycloakContainer keycloak = new KeycloakContainer().useTls();
```

The password for the provided Java KeyStore file is `changeit`.
See also [`KeycloakContainerHttpsTest.shouldStartKeycloakWithProvidedTlsCertAndKey`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerHttpsTest.java#L36).

The method `getAuthServerUrl()` will then return the HTTPS url.

### Custom TLS Cert and Key

Of course you can also provide your own certificate and key file for usage in this Testcontainer:

```java
@Container
private KeycloakContainer keycloak = new KeycloakContainer()
    .useTls("your_custom.crt", "your_custom.key");
```

See also [`KeycloakContainerHttpsTest.shouldStartKeycloakWithCustomTlsCertAndKey`](./src/test/java/dasniko/testcontainers/keycloak/KeycloakContainerHttpsTest.java#L44).

The method `getAuthServerUrl()` will also return the HTTPS url.

## Testing Custom Extensions

To ease extension testing, you can tell the Keycloak Testcontainer to detect extensions in a given classpath folder.
This allows to test extensions directly in the same module without a packaging step.

If you have your Keycloak extension code in the `src/main/java` folder, then the resulting classes will be generated to the `target/classes` folder.
To test your extensions you just need to tell `KeycloakContainer` to consider extensions from the `target/classes` folder.

Keycloak Testcontainer will then dynamically generate an exploded "jar file" with the extension code that is then picked up by Keycloak.

```java
private KeycloakContainer keycloak = new KeycloakContainer()
    .withExtensionClassesFrom("target/classes");
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


## Testcontainers & Keycloak version compatiblity

|Testcontainers-Keycloak |Testcontainers |Keycloak
|---|---|---
|1.2.0 |1.12.3 |8.0.1
|1.3.0 |1.12.3 |8.0.1
|1.3.1 |1.13.0 |9.0.2
|1.3.3 |1.13.0 |10.0.2
|1.4.0 |1.13.0 |11.0.2
|1.5.0 |1.15.1 |12.0.1
|1.6.0 |1.15.1 |12.0.1
|1.6.1 |1.15.1 |12.0.4
|1.7.0 |1.15.3 |13.0.0
|1.7.1 |1.15.3 |13.0.1

_There might also be other possible version configurations which will work._

See also the [Releases](https://github.com/dasniko/testcontainers-keycloak/releases) page for version and feature update notes.

## Credits

Many thanks to the creators and maintainers of [Testcontainers](https://www.testcontainers.org/).
You do an awesome job!

Same goes to the whole [Keycloak](https://www.keycloak.org/) team!

Kudos to [@thomasdarimont](https://github.com/thomasdarimont) for some inspiration for this project.

## License

MIT License

Copyright (c) 2019-2021 Niko Köbler

See [LICENSE](LICENSE) file for details.

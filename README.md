# Keycloak Testcontainer

A [Testcontainer](https://www.testcontainers.org/) implementation for [Keycloak](https://www.keycloak.org/) SSO.

_(Kudos to [@thomasdarimont](https://github.com/thomasdarimont) for some inspiration)_

Currently used: ![](https://img.shields.io/badge/Keycloak-8.0.1-blue)

## Setup

Simply spin up a default Keycloak instance:

```java
@Container
private KeycloakContainer keycloak = new KeycloakContainer();
```

Use another Keycloak Docker image/version than used in this Testcontainer:

```java
@Container
private KeycloakContainer keycloak = new KeycloakContainer("jboss/keycloak:7.0.0");
```

Power up a Keycloak instance with an existing realm JSON config file (from classpath):

```java
@Container
private KeycloakContainer keycloak = new KeycloakContainer()
    .withImportFile("test-realm.json");
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

This Keycloak Testcontainer comes with built-in TLS certificate, key and KeyStore files, located in the `resources` folder.
You can use this configuration by only configuring your testcontainer like this:

```java
@Container
private KeycloakContainer keycloak = new KeycloakContainer().useTls();
```

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

## Use it in your project

Currently, you can build and install the `testcontainers-keycloak` on your own with Maven and put it as a dependency to your project:

    $ mvn install

Add the dpendency in your `pom.xml`:

```xml
<dependency>
  <groupId>com.github.dasniko</groupId>
  <artifactId>testcontainers-keycloak</artifactId>
  <version>1.1</version>
</dependency>
```

Alternatively, you can get the package via [JitPack](https://jitpack.io/#dasniko/testcontainers-keycloak).
You only have to add the following repository in addition to the above mentioned dependency:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```

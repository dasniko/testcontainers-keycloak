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

## Integrate it into your project

Currently, you can build and install the `testcontainers-keycloak` on your own with Maven and put it as a dependency to your project.

Alternatively, you can get it via [JitPack](https://jitpack.io/#dasniko/testcontainers-keycloak).

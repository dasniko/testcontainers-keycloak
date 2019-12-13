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

Obtain the Auth-URL from the Keycloak container:

```java
String authServerUrl = keycloak.getAuthServerUrl();
```

Get a `org.keycloak.admin.client.Keycloak` (Keycloak admin client) object from the container, connected to the running instance, ready to be used for further configuration:

```java
Keycloak keycloakAdminClient = keycloak.getKeycloakAdminClient();
```

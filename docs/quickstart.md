# Quick Start

Get a Keycloak container running in your tests in minutes.

## Prerequisites

- Java 11 or higher
- Docker

## 1. Add the dependency

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

See [versions.md](versions.md) for the latest version.

## 2. Write your first test

```java
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MyKeycloakTest {

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26");

    @Test
    void shouldStartKeycloak() {
        String authServerUrl = keycloak.getAuthServerUrl();
        // use authServerUrl to configure your Keycloak client
    }
}
```

## 3. Import an existing realm

Place your realm export JSON in `src/test/resources/` and pass it to the container:

```java
@Container
static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26")
    .withRealmImportFile("/test-realm.json");
```

## 4. Get an admin client

```java
org.keycloak.admin.Keycloak adminClient = keycloak.getKeycloakAdminClient();
```

The `keycloak-admin-client` is a transitive dependency — no extra declaration needed.

## Next steps

See the [full README](../README.md) for all available configuration options: TLS, production mode,
custom extensions, debug support, and more.

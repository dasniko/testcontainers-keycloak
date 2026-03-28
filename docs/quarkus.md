# Using Keycloak Testcontainer with Quarkus

This guide shows how to integrate the Keycloak Testcontainer into Quarkus tests using `QuarkusTestResourceLifecycleManager`. It covers OIDC resource server configuration, multi-tenant setups, and tips for sharing a single container across test classes.

## Prerequisites

- Quarkus 3.x (2.x also supported with the same approach)
- `quarkus-oidc` or `quarkus-oidc-client` extension
- Testcontainers Keycloak dependency (see [Quick Start](quickstart.md))

## Dependency Setup

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

The `keycloak-admin-client` is a transitive dependency — no extra declaration needed.

## Pattern 1: `QuarkusTestResourceLifecycleManager` (Recommended)

Quarkus's test infrastructure starts the application before any `@BeforeAll` or `@BeforeEach` methods run. This means you cannot use `@DynamicPropertySource` the way Spring Boot does. Instead, implement `QuarkusTestResourceLifecycleManager` to start the container and inject config properties before the Quarkus application context boots.

### Implementing the lifecycle manager

```java
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

public class KeycloakTestResource implements QuarkusTestResourceLifecycleManager {

    private static final KeycloakContainer keycloak =
        new KeycloakContainer("quay.io/keycloak/keycloak:26")
            .withRealmImportFile("/test-realm.json");

    @Override
    public Map<String, String> start() {
        keycloak.start();
        return Map.of(
            "quarkus.oidc.auth-server-url",
                keycloak.getAuthServerUrl() + "/realms/test",
            "quarkus.oidc.client-id", "my-client",
            "quarkus.oidc.credentials.secret", "my-secret"
        );
    }

    @Override
    public void stop() {
        keycloak.stop();
    }
}
```

### Using the lifecycle manager in tests

```java
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(KeycloakTestResource.class)
class ProtectedResourceTest {

    @Test
    void shouldReturnUnauthorizedWithoutToken() {
        given()
            .when().get("/api/secured")
            .then().statusCode(401);
    }

    @Test
    void shouldAcceptValidBearerToken() {
        String token = obtainAccessToken("test", "test-client", "testuser", "testpass");
        given()
            .header("Authorization", "Bearer " + token)
            .when().get("/api/secured")
            .then().statusCode(200);
    }

    private String obtainAccessToken(String realm, String clientId,
                                      String username, String password) {
        // See "Obtaining access tokens" section below
        return "...";
    }
}
```

## Pattern 2: Shared container via `@QuarkusTestResource` with `restrictToAnnotatedClass = false`

By default, `@QuarkusTestResource` applies to all test classes in the test run when `restrictToAnnotatedClass` is set to `false`. This means only one container instance is started for all test classes — ideal for performance:

```java
public class KeycloakTestResource implements QuarkusTestResourceLifecycleManager {

    // Static field ensures only one container per JVM
    private static final KeycloakContainer keycloak =
        new KeycloakContainer("quay.io/keycloak/keycloak:26")
            .withRealmImportFile("/test-realm.json");

    @Override
    public Map<String, String> start() {
        keycloak.start();
        return Map.of(
            "quarkus.oidc.auth-server-url",
                keycloak.getAuthServerUrl() + "/realms/test"
        );
    }

    @Override
    public void stop() {
        keycloak.stop();
    }
}
```

Annotate any one test class (commonly a base class) with:

```java
@QuarkusTest
@QuarkusTestResource(value = KeycloakTestResource.class, restrictToAnnotatedClass = false)
class BaseIntegrationTest {
    // ...
}
```

All other `@QuarkusTest` classes in the same test suite will share the same Keycloak container automatically.

## Pattern 3: Injecting the container URL into test code

Sometimes test code itself needs the container URL (e.g., for token acquisition). Expose it via the `inject` callback:

```java
public class KeycloakTestResource implements QuarkusTestResourceLifecycleManager {

    private static final KeycloakContainer keycloak =
        new KeycloakContainer("quay.io/keycloak/keycloak:26")
            .withRealmImportFile("/test-realm.json");

    @Override
    public Map<String, String> start() {
        keycloak.start();
        return Map.of(
            "quarkus.oidc.auth-server-url",
                keycloak.getAuthServerUrl() + "/realms/test"
        );
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(
            keycloak,
            new TestInjector.AnnotatedAndMatchesType(InjectKeycloak.class, KeycloakContainer.class)
        );
    }

    @Override
    public void stop() {
        keycloak.stop();
    }
}
```

Define a custom qualifier annotation:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectKeycloak {}
```

Inject it in your test class:

```java
@QuarkusTest
@QuarkusTestResource(KeycloakTestResource.class)
class TokenAcquisitionTest {

    @InjectKeycloak
    KeycloakContainer keycloak;

    @Test
    void shouldAcquireToken() {
        String authServerUrl = keycloak.getAuthServerUrl();
        // use authServerUrl to call the token endpoint directly
    }
}
```

## Obtaining access tokens in tests

Use the Keycloak Admin Client (transitive dependency) for ROPC-based token acquisition in tests:

```java
import org.keycloak.admin.client.Keycloak;

private String obtainAccessToken(String realm, String clientId,
                                  String username, String password) {
    try (Keycloak client = Keycloak.getInstance(
            keycloak.getAuthServerUrl(),
            realm,
            username,
            password,
            clientId)) {
        return client.tokenManager().getAccessTokenString();
    }
}
```

For client credentials grants, use a plain HTTP call (no additional dependencies required):

```java
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

private String obtainClientCredentialsToken(String realm, String clientId,
                                             String clientSecret) throws Exception {
    String tokenUrl = keycloak.getAuthServerUrl()
        + "/realms/" + realm + "/protocol/openid-connect/token";
    String body = "grant_type=client_credentials"
        + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
        + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

    HttpResponse<String> response = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    String json = response.body();
    int start = json.indexOf("\"access_token\":\"") + 16;
    int end = json.indexOf('"', start);
    return json.substring(start, end);
}
```

## Multi-tenant OIDC configuration

When your Quarkus application uses multiple OIDC tenants (`quarkus.oidc.tenants.*`), add each tenant's properties in the `start()` return map:

```java
@Override
public Map<String, String> start() {
    keycloak.start();
    String base = keycloak.getAuthServerUrl();
    return Map.of(
        // Default tenant
        "quarkus.oidc.auth-server-url",       base + "/realms/default",
        "quarkus.oidc.client-id",             "default-client",
        // Named tenant
        "quarkus.oidc.tenants.partner.auth-server-url",  base + "/realms/partner",
        "quarkus.oidc.tenants.partner.client-id",        "partner-client"
    );
}
```

## OIDC client (service-to-service calls)

For applications using `quarkus-oidc-client` to call downstream services:

```java
@Override
public Map<String, String> start() {
    keycloak.start();
    String base = keycloak.getAuthServerUrl() + "/realms/test";
    return Map.of(
        // Resource server side
        "quarkus.oidc.auth-server-url",                        base,
        // OIDC client side (outgoing token requests)
        "quarkus.oidc-client.auth-server-url",                 base,
        "quarkus.oidc-client.client-id",                       "service-client",
        "quarkus.oidc-client.credentials.secret",              "service-secret",
        "quarkus.oidc-client.grant.type",                      "client"
    );
}
```

## Using HTTPS (TLS)

Enable TLS on the container and trust the built-in self-signed certificate in Quarkus tests:

```java
private static final KeycloakContainer keycloak =
    new KeycloakContainer("quay.io/keycloak/keycloak:26")
        .useTls()
        .withRealmImportFile("/test-realm.json");

@Override
public Map<String, String> start() {
    keycloak.start();
    // getAuthServerUrl() returns HTTPS when TLS is enabled
    return Map.of(
        "quarkus.oidc.auth-server-url",
            keycloak.getAuthServerUrl() + "/realms/test",
        "quarkus.tls.trust-all", "true"   // only for tests — never in production
    );
}
```

> **Note:** `quarkus.tls.trust-all=true` is acceptable in isolated test environments. For stricter setups, configure a custom truststore pointing to the `tls.jks` bundled with the Keycloak Testcontainer JAR (`/tls.jks` on the classpath, password: `changeit`).

## Realm configuration tips

- Export a realm from a running Keycloak instance via **Realm Settings → Action → Partial export** (include clients and roles).
- Place the JSON file in `src/test/resources/` and reference it with a leading slash: `.withRealmImportFile("/my-realm.json")`.
- If the realm export contains a master-realm admin user definition, call `.withBootstrapAdminDisabled()` to prevent conflicts.
- Quarkus dev services can also spin up a Keycloak instance automatically during `quarkus:dev` / `quarkus:test`. Testcontainers Keycloak is the right choice when you need fine-grained control over the realm and client configuration or when running with `@QuarkusIntegrationTest`.

## See also

- [Quick Start](quickstart.md)
- [Spring Boot integration guide](spring-boot.md)
- [Full README](../README.md)
- [Quarkus OIDC guide](https://quarkus.io/guides/security-oidc-bearer-token-authentication)
- [Quarkus test resources guide](https://quarkus.io/guides/getting-started-testing#quarkus-test-resource)

---

_This guide was created with the assistance of [Claude](https://claude.ai) (Anthropic)._

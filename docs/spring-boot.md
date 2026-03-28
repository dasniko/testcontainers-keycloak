# Using Keycloak Testcontainer with Spring Boot

This guide shows how to integrate the Keycloak Testcontainer into Spring Boot tests, covering the most common patterns for dynamic property injection and OAuth2/OIDC resource server configuration.

## Prerequisites

- Spring Boot 2.7+ (Spring Boot 3.x / 4.x recommended)
- Java 17+ (required by Spring Boot 4.0)
- Testcontainers Keycloak dependency (see [Quick Start](quickstart.md))
- Spring Security OAuth2 Resource Server or Spring Security OAuth2 Client on the classpath

## Spring Boot version compatibility

| Spring Boot | Java | Notes |
|---|---|---|
| 2.7.x | 11+ | `@DynamicPropertySource` and `ApplicationContextInitializer` patterns work |
| 3.x | 17+ | Same patterns; `@ServiceConnection` introduced but no Keycloak support built in |
| 4.x | 17+ | `@ServiceConnection` preferred where available; no built-in Keycloak/OIDC support, so `@DynamicPropertySource` remains the right approach |

> **Spring Boot 4.x note:** `@ServiceConnection` is now the preferred pattern for containers that Spring Boot knows about natively (databases, Redis, etc.), but there is no built-in `@ServiceConnection` support for Keycloak or generic OIDC providers. `@DynamicPropertySource` is therefore still the canonical approach for Keycloak integration tests across all Spring Boot versions.

## Dependency Setup

Add the Keycloak Testcontainer to your test dependencies alongside Spring Boot's testing support:

**Maven:**
```xml
<dependency>
  <groupId>com.github.dasniko</groupId>
  <artifactId>testcontainers-keycloak</artifactId>
  <version>VERSION</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
```

**Gradle (Kotlin DSL):**
```kotlin
testImplementation("com.github.dasniko:testcontainers-keycloak:VERSION")
testImplementation("org.testcontainers:junit-jupiter")
```

## Pattern 1: `@DynamicPropertySource` (Recommended, all Spring Boot versions)

`@DynamicPropertySource` injects the container's dynamic URLs into the Spring `Environment` before the application context is created. It works identically across Spring Boot 2.7, 3.x, and 4.x.

### Resource Server (JWT validation)

A typical Spring Boot resource server configured with `spring-security-oauth2-resource-server` uses the issuer URI to fetch the OIDC discovery document. Set it to Keycloak's realm URL:

```java
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ResourceServerIntegrationTest {

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4")
        .withRealmImportFile("/test-realm.json");

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> keycloak.getAuthServerUrl() + "/realms/test");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void shouldReturnUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/secured"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptValidBearerToken() throws Exception {
        String token = obtainAccessToken();
        mockMvc.perform(get("/api/secured")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    private String obtainAccessToken() {
        // Use Keycloak admin client or a direct token request
        // to obtain a token from the running container.
        // See "Obtaining tokens in tests" section below.
        return "...";
    }
}
```

Your `application.properties` / `application.yml` in `src/test/resources` can still contain a placeholder — the `@DynamicPropertySource` value takes precedence:

```properties
# src/test/resources/application-test.properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/test
```

### OAuth2 Client (Authorization Code / Client Credentials)

For applications acting as an OAuth2 client (e.g., calling a downstream API with a client credentials token):

```java
@DynamicPropertySource
static void keycloakProperties(DynamicPropertyRegistry registry) {
    String issuerUri = keycloak.getAuthServerUrl() + "/realms/test";
    registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", () -> issuerUri);
    registry.add("spring.security.oauth2.client.registration.keycloak.client-id", () -> "my-client");
    registry.add("spring.security.oauth2.client.registration.keycloak.client-secret", () -> "my-secret");
    registry.add("spring.security.oauth2.client.registration.keycloak.authorization-grant-type",
        () -> "client_credentials");
}
```

## Pattern 2: `ApplicationContextInitializer` (Classic Spring approach)

Before `@DynamicPropertySource` was introduced, the canonical Spring approach was a custom `ApplicationContextInitializer`. This pattern is still useful when you need to share a single container instance across multiple test classes via a base class or a Spring `@TestConfiguration`. Both `ApplicationContextInitializer` and `TestPropertyValues` are available in all Spring Boot versions including 4.x.

```java
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = ResourceServerTest.Initializer.class)
class ResourceServerTest {

    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4")
        .withRealmImportFile("/test-realm.json");

    static {
        keycloak.start();
    }

    static class Initializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=" +
                    keycloak.getAuthServerUrl() + "/realms/test"
            ).applyTo(ctx.getEnvironment());
        }
    }
}
```

> **Note:** When managing the container lifecycle manually (without `@Testcontainers` / `@Container`), call `keycloak.start()` explicitly (e.g., in a `static` block) and ensure `keycloak.stop()` is called on teardown. Using a `static` field on a base class shared across test classes is a common pattern for container reuse.

## Pattern 3: Shared container via abstract base class

When multiple test classes need the same Keycloak configuration, extract the container into a shared base:

```java
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
abstract class AbstractKeycloakIntegrationTest {

    @Container
    static final KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4")
        .withRealmImportFile("/test-realm.json");

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> keycloak.getAuthServerUrl() + "/realms/test");
    }
}
```

Testcontainers will reuse the single container across all subclasses within the same JVM (Ryuk will stop it after the JVM exits).

```java
@SpringBootTest
@AutoConfigureMockMvc
class MyFirstTest extends AbstractKeycloakIntegrationTest {
    // ...
}

@SpringBootTest
class MySecondTest extends AbstractKeycloakIntegrationTest {
    // ...
}
```

## Obtaining access tokens in tests

Use the Keycloak Admin Client (a transitive dependency) or a plain HTTP call to obtain tokens for your test requests. Direct token acquisition with the Resource Owner Password Credentials (ROPC) grant is useful in tests:

```java
import org.keycloak.admin.client.Keycloak;

private String obtainAccessToken(String realm, String clientId, String username, String password) {
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

Or, using the plain Java HTTP client (no extra dependencies):

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

private String obtainAccessToken(String realm, String clientId, String clientSecret)
        throws Exception {
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

    // Parse "access_token" from the JSON response
    // (use Jackson/Gson if already on classpath, or a simple regex for test code)
    String json = response.body();
    int start = json.indexOf("\"access_token\":\"") + 16;
    int end = json.indexOf('"', start);
    return json.substring(start, end);
}
```

## Using HTTPS (TLS) in Spring Boot tests

If your production configuration enforces HTTPS, enable TLS on the test container and point Spring Security at the HTTPS issuer URI:

```java
@Container
static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.4")
    .useTls()
    .withRealmImportFile("/test-realm.json");

@DynamicPropertySource
static void keycloakProperties(DynamicPropertyRegistry registry) {
    // getAuthServerUrl() returns an HTTPS URL when TLS is enabled
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> keycloak.getAuthServerUrl() + "/realms/test");

    // Trust the built-in self-signed certificate
    registry.add("spring.ssl.bundle.jks.keycloak.truststore.location",
        () -> "classpath:tls.jks");
    registry.add("spring.ssl.bundle.jks.keycloak.truststore.password", () -> "changeit");
}
```

The built-in `tls.jks` truststore (password: `changeit`) is available from the Keycloak Testcontainer JAR on the classpath.

## Spring Boot 4.x specifics

### `@MockitoBean` / `@MockitoSpyBean` replace `@MockBean` / `@SpyBean`

Spring Boot 4.0 deprecated `@MockBean` and `@SpyBean` in favour of the Spring Framework native `@MockitoBean` and `@MockitoSpyBean`. Update any test classes that mock security-related beans:

```java
// Spring Boot 3.x (still works but deprecated in 4.x)
@MockBean
JwtDecoder jwtDecoder;

// Spring Boot 4.x
@MockitoBean
JwtDecoder jwtDecoder;
```

### Stricter JWT `typ` header validation

Spring Boot 4.0 tightened JWT validation: the `typ` header can no longer be disabled via a `JwkSetUriJwtDecoderBuilderCustomizer`. Keycloak issues access tokens with `typ: Bearer` by default, which passes this check without any extra configuration. If you have a custom JWT decoder that previously suppressed type validation, you will need to re-evaluate that configuration when migrating to Spring Boot 4.x.

### Mocking the security layer without a running Keycloak

For fast unit-style tests that only need a mocked OAuth2 principal (no container required), Spring Security's MockMvc support works unchanged in Boot 4.x:

```java
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

mockMvc.perform(get("/api/secured")
        .with(jwt()
            .jwt(j -> j.claim("realm_access",
                Map.of("roles", List.of("user"))))))
    .andExpect(status().isOk());
```

Use this pattern to complement, not replace, the full-stack integration tests that use a real Keycloak container.

## Realm configuration tips

- Export a realm from a running Keycloak instance via **Realm Settings → Action → Partial export** (include clients and roles).
- Place the JSON file in `src/test/resources/` and reference it with a leading slash: `.withRealmImportFile("/my-realm.json")`.
- If the realm export contains a master-realm admin user definition, call `.withBootstrapAdminDisabled()` to prevent conflicts.

## See also

- [Quick Start](quickstart.md)
- [Quarkus integration guide](quarkus.md)
- [Full README](../README.md)
- [Spring Security OAuth2 Resource Server docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Spring TestContext Framework — Dynamic Property Sources](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/dynamic-property-sources.html)
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

---

_This guide was created with the assistance of [Claude](https://claude.ai) (Anthropic)._

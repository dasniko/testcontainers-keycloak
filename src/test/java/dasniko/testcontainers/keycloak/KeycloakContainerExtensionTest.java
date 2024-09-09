package dasniko.testcontainers.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.extensions.oidcmapper.TestOidcProtocolMapper;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Test;
import org.keycloak.TokenVerifier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.models.Constants;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dasniko.testcontainers.keycloak.KeycloakContainerTest.TEST_REALM_JSON;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

public class KeycloakContainerExtensionTest {

    @Test
    public void shouldStartKeycloakWithNonExistingExtensionClassFolder() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .withProviderClassesFrom("target/does_not_exist")) {
            keycloak.start();
        }
    }

    /**
     * Deploys the Keycloak extensions from the test-classes folder into the created Keycloak container.
     */
    @Test
    public void shouldDeployProvider() throws Exception {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            // this would normally be just "target/classes"
            .withProviderClassesFrom("target/test-classes")
            .withRealmImportFile(TEST_REALM_JSON)) {
            keycloak.start();

            Keycloak keycloakClient = keycloak.getKeycloakAdminClient();

            configureCustomOidcProtocolMapper(keycloakClient);

            keycloakClient.tokenManager().refreshToken();
            AccessTokenResponse tokenResponse = keycloakClient.tokenManager().getAccessToken();

            // parse the received access-token
            TokenVerifier<AccessToken> verifier = TokenVerifier.create(tokenResponse.getToken(), AccessToken.class);
            verifier.parse();

            // check for the custom claim
            AccessToken accessToken = verifier.getToken();
            String customClaimValue = (String)accessToken.getOtherClaims().get(TestOidcProtocolMapper.CUSTOM_CLAIM_NAME);
            System.out.printf("Custom Claim name %s=%s%n", TestOidcProtocolMapper.CUSTOM_CLAIM_NAME, customClaimValue);
            assertThat(customClaimValue, notNullValue());
            assertThat(customClaimValue, startsWith("testdata:"));
        }
    }

    @Test
    public void shouldDeployProviderAndCallCustomEndpoint() throws Exception {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            // this would normally be just "target/classes"
            .withProviderClassesFrom("target/test-classes")) {
            keycloak.start();

            ObjectMapper objectMapper = new ObjectMapper();
            String uri = keycloak.getAuthServerUrl() + "/realms/master/test-resource/hello";

            // test the "public" endpoint
            Map<String, String> result = objectMapper.readValue(new URL(uri), new TypeReference<>() {});
            assertThat(result.get("hello"), is("master"));

            // and now the secured endpoint, first we need a valid token
            Keycloak keycloakClient = keycloak.getKeycloakAdminClient();
            AccessTokenResponse accessTokenResponse = keycloakClient.tokenManager().getAccessToken();

            URL url = new URL(keycloak.getAuthServerUrl() + "/realms/master/test-resource/hello-auth");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessTokenResponse.getToken());

            Map<String, String> authResult = objectMapper.readValue(conn.getInputStream(), new TypeReference<>() {});
            assertThat(authResult.get("hello"), is("admin"));
        }
    }

    @Test
    public void shouldDeployProviderWithDependencyAndCallCustomEndpoint() throws Exception {
        List<File> dependencies = Maven.resolver()
            .loadPomFromFile("./pom.xml")
            .resolve("net.datafaker:datafaker")
            .withoutTransitivity().asList(File.class);

        try (KeycloakContainer keycloak = new KeycloakContainer()
            .withProviderClassesFrom("target/test-classes")
            .withProviderLibsFrom(dependencies)) {
            keycloak.start();

            ObjectMapper objectMapper = new ObjectMapper();
            String uri = keycloak.getAuthServerUrl() + "/realms/master/yoda/quote";

            Map<String, String> result = objectMapper.readValue(new URL(uri), new TypeReference<>() {});
            String quote = result.get("yoda");
            assertThat(quote, not(emptyOrNullString()));
            System.out.printf("Yoda says: %s\n", quote);
        }
    }

    @Test
    public void shouldCacheStaticContentPerDefault() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .withProviderClassesFrom("target/test-classes")) {
            keycloak.start();
            given().when().get(getProjectLogoUrl(keycloak))
                .then().statusCode(200).header("Cache-Control", containsString("max-age=2592000"));
        }
    }

    @Test
    public void shouldNotCacheStaticContentWithDisabledCaching() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .withProviderClassesFrom("target/test-classes")
            .withDisabledCaching()) {
            keycloak.start();
            given().when().get(getProjectLogoUrl(keycloak))
                .then().statusCode(200).header("Cache-Control", "no-cache");
        }
    }

    private String getProjectLogoUrl(KeycloakContainer keycloak) {
        ObjectMapper objectMapper = new ObjectMapper();
        String uri = keycloak.getAuthServerUrl() + "/realms/master/test-resource/theme-root";
        try {
            Map<String, String> themeRoot = objectMapper.readValue(new URL(uri), new TypeReference<>(){});
            return themeRoot.get("url") + "/login/keycloak.v2/img/keycloak-logo-text.png";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Configures the {@link TestOidcProtocolMapper} to the given client in the given realm.
     */
    static void configureCustomOidcProtocolMapper(Keycloak keycloakClient) {
        RealmResource realm = keycloakClient.realm(KeycloakContainer.MASTER_REALM);
        ClientRepresentation client = realm.clients().findByClientId(KeycloakContainer.ADMIN_CLI_CLIENT).get(0);

        // first we have to disable lightweight access_token, which is default now in the admin-cli client
        Map<String, String> attributes = client.getAttributes();
        attributes.put(Constants.USE_LIGHTWEIGHT_ACCESS_TOKEN_ENABLED, Boolean.FALSE.toString());
        client.setAttributes(attributes);
        realm.clients().get(client.getId()).update(client);

        ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        mapper.setProtocolMapper(TestOidcProtocolMapper.ID);
        mapper.setName("test-mapper");
        Map<String, String> config = new HashMap<>();
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN, "true");
        mapper.setConfig(config);

        realm.clients().get(client.getId()).getProtocolMappers().createMapper(mapper).close();
    }
}

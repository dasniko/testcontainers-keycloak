package dasniko.testcontainers.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.extensions.oidcmapper.TestOidcProtocolMapper;
import org.junit.jupiter.api.Test;
import org.keycloak.TokenVerifier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static dasniko.testcontainers.keycloak.KeycloakContainerTest.TEST_REALM_JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
        shouldDeploy(kc ->
            // this would normally be just "target/classes"
            kc.withProviderClassesFrom("target/test-classes"));
    }

    private void shouldDeploy(Function<KeycloakContainer, KeycloakContainer> configurator) throws Exception {
        try (KeycloakContainer keycloak = configurator.apply(new KeycloakContainer())
            .withRealmImportFile(TEST_REALM_JSON)) {
            keycloak.start();

            Keycloak keycloakClient = keycloak.getKeycloakAdminClient();

            RealmResource realm = keycloakClient.realm(KeycloakContainer.MASTER_REALM);
            ClientRepresentation client = realm.clients().findByClientId(KeycloakContainer.ADMIN_CLI_CLIENT).get(0);

            configureCustomOidcProtocolMapper(realm, client);

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
        shouldDeployAndCallCustomEndpoint(kc ->
            // this would normally be just "target/classes"
            kc.withProviderClassesFrom("target/test-classes")
        );
    }

    private void shouldDeployAndCallCustomEndpoint(Function<KeycloakContainer, KeycloakContainer> configurator) throws Exception {
        try (KeycloakContainer keycloak = configurator.apply(new KeycloakContainer())) {
            keycloak.start();

            ObjectMapper objectMapper = new ObjectMapper();
            String uri = keycloak.getAuthServerUrl() + "realms/master/test-resource/hello";

            // test the "public" endpoint
            Map<String, String> result = objectMapper.readValue(new URL(uri), new TypeReference<>() {});
            assertThat(result.get("hello"), is("master"));

            // and now the secured endpoint, first we need a valid token
            Keycloak keycloakClient = keycloak.getKeycloakAdminClient();
            AccessTokenResponse accessTokenResponse = keycloakClient.tokenManager().getAccessToken();

            URL url = new URL(keycloak.getAuthServerUrl() + "realms/master/test-resource/hello-auth");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessTokenResponse.getToken());

            Map<String, String> authResult = objectMapper.readValue(conn.getInputStream(), new TypeReference<>() {});
            assertThat(authResult.get("hello"), is("admin"));
        }
    }

    /**
     * Configures the {@link TestOidcProtocolMapper} to the given client in the given realm.
     */
    static void configureCustomOidcProtocolMapper(RealmResource realm, ClientRepresentation client) {

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

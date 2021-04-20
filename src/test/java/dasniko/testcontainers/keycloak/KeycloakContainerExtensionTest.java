package dasniko.testcontainers.keycloak;

import dasniko.testcontainers.keycloak.extensions.oidcmapper.TestOidcProtocolMapper;
import org.junit.Test;
import org.keycloak.TokenVerifier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;

import java.util.HashMap;
import java.util.Map;

import static dasniko.testcontainers.keycloak.KeycloakContainerTest.ADMIN_CLI;
import static dasniko.testcontainers.keycloak.KeycloakContainerTest.MASTER;
import static dasniko.testcontainers.keycloak.KeycloakContainerTest.TEST_REALM_JSON;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

public class KeycloakContainerExtensionTest {

    @Test
    public void shouldStartKeycloakWithNonExistingExtensionClassFolder() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .withExtensionClassesFrom("target/does_not_exist")) {
            keycloak.start();
        }
    }

    /**
     * Deploys the Keycloak extensions from the test-classes folder into the create Keycloak container.
     *
     * @throws Exception
     */
    @Test
    public void shouldDeployExtension() throws Exception {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .withRealmImportFile(TEST_REALM_JSON)
            // this would normally be just "target/classes"
            .withExtensionClassesFrom("target/test-classes")) {
            keycloak.start();

            Keycloak keycloakClient = Keycloak.getInstance(keycloak.getAuthServerUrl(), MASTER,
                keycloak.getAdminUsername(), keycloak.getAdminPassword(), ADMIN_CLI);

            RealmResource realm = keycloakClient.realm(MASTER);
            ClientRepresentation client = realm.clients().findByClientId(ADMIN_CLI).get(0);

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

    /**
     * Configures the {@link TestOidcProtocolMapper} to the given client in the given realm.
     *
     * @param realm
     * @param client
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

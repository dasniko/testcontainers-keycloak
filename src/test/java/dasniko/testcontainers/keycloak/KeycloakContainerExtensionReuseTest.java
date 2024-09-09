package dasniko.testcontainers.keycloak;

import dasniko.testcontainers.keycloak.extensions.oidcmapper.TestOidcProtocolMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.TokenVerifier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;

import static dasniko.testcontainers.keycloak.KeycloakContainerTest.TEST_REALM_JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tests reusable containers support for {@link KeycloakContainer}.
 */
public class KeycloakContainerExtensionReuseTest {

    public static final KeycloakContainer KEYCLOAK = new KeycloakContainer()
        .withRealmImportFile(TEST_REALM_JSON)
        // this would normally be just "target/classes"
        .withProviderClassesFrom("target/test-classes")
        // this enables KeycloakContainer reuse across tests
        .withReuse(true);

    @BeforeAll
    public static void beforeAll() {
        KEYCLOAK.start();
        KeycloakContainerExtensionTest.configureCustomOidcProtocolMapper(KEYCLOAK);
    }

    @AfterAll
    public static void afterAll() {
        KEYCLOAK.stop();
    }

    @Test
    public void shouldDeployExtensionWithReuse1() throws Exception {
        simpleOidcProtocolMapperTest();
    }

    @Test
    public void shouldDeployExtensionWithReuse2() throws Exception {
        simpleOidcProtocolMapperTest();
    }

    @Test
    public void shouldDeployExtensionWithReuse3() throws Exception {
        simpleOidcProtocolMapperTest();
    }

    private void simpleOidcProtocolMapperTest() throws Exception {

        Keycloak keycloakClient = KEYCLOAK.getKeycloakAdminClient();

        keycloakClient.tokenManager().grantToken();

        keycloakClient.tokenManager().refreshToken();
        AccessTokenResponse tokenResponse = keycloakClient.tokenManager().getAccessToken();

        // parse the received access-token
        TokenVerifier<AccessToken> verifier = TokenVerifier.create(tokenResponse.getToken(), AccessToken.class);
        verifier.parse();

        // check for the custom claim
        AccessToken accessToken = verifier.getToken();
        String customClaimValue = (String) accessToken.getOtherClaims().get(TestOidcProtocolMapper.CUSTOM_CLAIM_NAME);
        System.out.printf("Custom Claim name %s=%s%n", TestOidcProtocolMapper.CUSTOM_CLAIM_NAME, customClaimValue);
        assertThat(customClaimValue, notNullValue());
        assertThat(customClaimValue, startsWith("testdata:"));
    }
}

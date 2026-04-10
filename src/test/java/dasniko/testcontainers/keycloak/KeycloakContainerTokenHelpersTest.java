package dasniko.testcontainers.keycloak;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static dasniko.testcontainers.keycloak.KeycloakContainerTest.KC_IMAGE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainerTokenHelpersTest {

    static final String TEST_REALM = "test";
    static final String CLIENT_ID = "test-client";
    static final String CLIENT_SECRET = "test-secret";
    static final String USERNAME = "testuser";
    static final String PASSWORD = "testpass";

    static final KeycloakContainer KEYCLOAK = new KeycloakContainer(KC_IMAGE)
        .withRealmImportFile("/test-realm.json");

    @BeforeAll
    static void setup() {
        KEYCLOAK.start();

        RealmResource realm = KEYCLOAK.getKeycloakAdminClient().realm(TEST_REALM);

        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(CLIENT_ID);
        client.setSecret(CLIENT_SECRET);
        client.setPublicClient(false);
        client.setServiceAccountsEnabled(true);
        client.setDirectAccessGrantsEnabled(true);
        client.setEnabled(true);
        realm.clients().create(client).close();

        UserRepresentation user = new UserRepresentation();
        user.setUsername(USERNAME);
        user.setEmail(USERNAME + "@testcontainers.dasniko.de");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        try (Response response = realm.users().create(user)) {
            String userId = CreatedResponseUtil.getCreatedId(response);
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(PASSWORD);
            credential.setTemporary(false);
            realm.users().get(userId).resetPassword(credential);
        }
    }

    @AfterAll
    static void stopKeycloak() {
        KEYCLOAK.stop();
    }

    @Test
    void shouldGetAccessTokenWithPassword() {
        String token = KEYCLOAK.getAccessToken(TEST_REALM, CLIENT_ID, CLIENT_SECRET, USERNAME, PASSWORD);
        assertThat(token, not(emptyOrNullString()));
        assertThat(extractIssuerFromToken(token), equalTo(KEYCLOAK.getIssuerUrl(TEST_REALM)));
    }

    @Test
    void shouldGetClientCredentialsToken() {
        String token = KEYCLOAK.getClientCredentialsToken(TEST_REALM, CLIENT_ID, CLIENT_SECRET);
        assertThat(token, not(emptyOrNullString()));
        assertThat(extractIssuerFromToken(token), equalTo(KEYCLOAK.getIssuerUrl(TEST_REALM)));
    }

    @Test
    void shouldGetFullTokenResponseWithPassword() {
        TokenResponse response = KEYCLOAK.getTokenResponse(TEST_REALM, CLIENT_ID, CLIENT_SECRET, USERNAME, PASSWORD);
        assertThat(response.getAccessToken(), not(emptyOrNullString()));
        assertThat(response.getRefreshToken(), not(emptyOrNullString()));
        assertThat(response.getExpiresIn(), greaterThan(0));
        assertThat(response.getTokenType(), equalToIgnoringCase("bearer"));
    }

    @Test
    void shouldGetFullTokenResponseWithClientCredentials() {
        TokenResponse response = KEYCLOAK.getClientCredentialsTokenResponse(TEST_REALM, CLIENT_ID, CLIENT_SECRET);
        assertThat(response.getAccessToken(), not(emptyOrNullString()));
        assertThat(response.getExpiresIn(), greaterThan(0));
        assertThat(response.getTokenType(), equalToIgnoringCase("bearer"));
    }

    private static String extractIssuerFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode claims = new ObjectMapper().readTree(payload);
            return claims.get("iss").asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract 'iss' claim from token", e);
        }
    }

    @Test
    void shouldReturnNullRefreshTokenForClientCredentials() {
        TokenResponse response = KEYCLOAK.getClientCredentialsTokenResponse(TEST_REALM, CLIENT_ID, CLIENT_SECRET);
        // client_credentials grant does not issue a refresh token by default in Keycloak
        assertThat(response.getRefreshToken(), nullValue());
    }
}

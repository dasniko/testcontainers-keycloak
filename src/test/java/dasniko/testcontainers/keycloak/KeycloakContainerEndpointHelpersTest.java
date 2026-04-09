package dasniko.testcontainers.keycloak;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static dasniko.testcontainers.keycloak.KeycloakContainerTest.KC_IMAGE;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainerEndpointHelpersTest {

    static final KeycloakContainer KEYCLOAK = new KeycloakContainer(KC_IMAGE);

    @BeforeAll
    static void startKeycloak() {
        KEYCLOAK.start();
    }

    @AfterAll
    static void stopKeycloak() {
        KEYCLOAK.stop();
    }

    @Test
    void shouldReturnOpenIdConfigurationUrl() {
        String url = KEYCLOAK.getOpenIdConfigurationUrl("master");
        assertThat(url, startsWith(KEYCLOAK.getAuthServerUrl()));
        assertThat(url, endsWith("/realms/master/.well-known/openid-configuration"));
        given().when().get(url).then().statusCode(200);
    }

    @Test
    void shouldReturnIssuerUrl() {
        String issuerUrl = KEYCLOAK.getIssuerUrl("master");
        assertThat(issuerUrl, equalTo(KEYCLOAK.getAuthServerUrl() + "/realms/master"));
    }

    @Test
    void shouldReturnTokenEndpoint() {
        String tokenEndpoint = KEYCLOAK.getTokenEndpoint("master");
        assertThat(tokenEndpoint, startsWith(KEYCLOAK.getAuthServerUrl()));
        assertThat(tokenEndpoint, endsWith("/realms/master/protocol/openid-connect/token"));
    }

    @Test
    void shouldReturnJwksUri() {
        String jwksUri = KEYCLOAK.getJwksUri("master");
        assertThat(jwksUri, startsWith(KEYCLOAK.getAuthServerUrl()));
        assertThat(jwksUri, endsWith("/realms/master/protocol/openid-connect/certs"));
        given().when().get(jwksUri).then().statusCode(200);
    }

    @Test
    void shouldReturnUserInfoEndpoint() {
        String userInfoEndpoint = KEYCLOAK.getUserInfoEndpoint("master");
        assertThat(userInfoEndpoint, startsWith(KEYCLOAK.getAuthServerUrl()));
        assertThat(userInfoEndpoint, endsWith("/realms/master/protocol/openid-connect/userinfo"));
    }

    @Test
    void shouldReturnConsistentValuesOnRepeatedCalls() {
        assertThat(KEYCLOAK.getIssuerUrl("master"), equalTo(KEYCLOAK.getIssuerUrl("master")));
        assertThat(KEYCLOAK.getTokenEndpoint("master"), equalTo(KEYCLOAK.getTokenEndpoint("master")));
        assertThat(KEYCLOAK.getJwksUri("master"), equalTo(KEYCLOAK.getJwksUri("master")));
        assertThat(KEYCLOAK.getUserInfoEndpoint("master"), equalTo(KEYCLOAK.getUserInfoEndpoint("master")));
    }
}

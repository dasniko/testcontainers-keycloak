package dasniko.testcontainers.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import static dasniko.testcontainers.keycloak.KeycloakContainerTest.TEST_REALM_JSON;
import dasniko.testcontainers.keycloak.extensions.oidcmapper.TestOidcProtocolMapper;
import dasniko.testcontainers.keycloak.extensions.somespi.SomeKeycloakSpiFactory;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.TokenVerifier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.info.ProviderRepresentation;
public class KeycloakContainerExtensionTest {

    @Test
    public void shouldStartKeycloakWithNonExistingExtensionClassFolder() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .withProviderClassesFrom("target/does_not_exist")) {
            keycloak.start();
        }
    }

    private static Stream<Arguments> providerClassesAndResourcesLocationConfig() {

        return Stream.of(
                // this would normally be just "target/classes"
                Arguments.of(Set.of("target/test-classes")),
                Arguments.of(Set.of("target/test-classes", "")),
                Arguments.of(Set.of("target/test-classes", "doesnt/exist"))
        );
    }

    /**
     * Deploys the Keycloak extensions from the test-classes folder into the created Keycloak container.
     */
    @ParameterizedTest
    @MethodSource("providerClassesAndResourcesLocationConfig")
    public void shouldDeployProvider(final Set<String> paths) throws Exception {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            // this would normally be just "target/classes"
            .withProviderClassesAndResourcesFrom(paths.toArray(new String[]{}))
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
    void shouldDeployProviderFromMultipleClassesAndMetaInfLocations() {
        try (final KeycloakContainer keycloak = new KeycloakContainer()) {

            keycloak.withProviderClassesAndResourcesFrom("target/test-classes", "target/test-classes/imaginarygradlebuilddir");

            keycloak.start();
            final Map<String, ProviderRepresentation> restSpis = keycloak.getKeycloakAdminClient()
                    .serverInfo()
                    .getInfo()
                    .getProviders()
                    .get("realm-restapi-extension")
                    .getProviders();

            assertThat(restSpis, org.hamcrest.collection.IsMapContaining.hasKey(SomeKeycloakSpiFactory.ID));
        }
    }

    @Test
    public void shouldDeployProviderWithDependencyAndCallCustomEndpoint() throws Exception {
        List<File> dependencies = Maven.resolver()
            .loadPomFromFile("./pom.xml")
            .resolve("com.github.javafaker:javafaker")
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

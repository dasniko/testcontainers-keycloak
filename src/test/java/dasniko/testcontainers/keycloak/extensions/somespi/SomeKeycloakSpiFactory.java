package dasniko.testcontainers.keycloak.extensions.somespi;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class SomeKeycloakSpiFactory implements RealmResourceProviderFactory {

    @SuppressWarnings("unused")
    public static final String ID = "some-keycloak-spi";

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new SomeKeycloakSpi();
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return ID;
    }
}

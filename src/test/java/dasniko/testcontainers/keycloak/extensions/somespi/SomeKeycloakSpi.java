package dasniko.testcontainers.keycloak.extensions.somespi;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.keycloak.services.resource.RealmResourceProvider;

public class SomeKeycloakSpi implements RealmResourceProvider {

    @Override
    public Object getResource() {
        return this;
    }

    @Override
    public void close() {}

    @SuppressWarnings("unused")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String get() {
        return "Hello world!";
    }

}

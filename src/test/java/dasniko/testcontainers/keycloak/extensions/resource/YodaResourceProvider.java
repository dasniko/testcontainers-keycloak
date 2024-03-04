package dasniko.testcontainers.keycloak.extensions.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.datafaker.Faker;
import org.keycloak.services.resource.RealmResourceProvider;

import java.util.Collections;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class YodaResourceProvider implements RealmResourceProvider {

    @Override
    public Object getResource() {
        return this;
    }

    @Override
    public void close() {
    }

    @GET
    @Path("quote")
    @Produces(MediaType.APPLICATION_JSON)
    public Response hello() {
        return Response.ok(Collections.singletonMap("yoda", new Faker().yoda().quote())).build();
    }

}

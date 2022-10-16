package dasniko.testcontainers.keycloak.extensions.resource;

import net.datafaker.Faker;
import org.keycloak.services.resource.RealmResourceProvider;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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
        return Response.ok(Collections.singletonMap("yoda", Faker.instance().yoda().quote())).build();
    }

}

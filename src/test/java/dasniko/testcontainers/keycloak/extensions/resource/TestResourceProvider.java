package dasniko.testcontainers.keycloak.extensions.resource;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager.AuthResult;
import org.keycloak.services.resource.RealmResourceProvider;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class TestResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public TestResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return this;
    }

    @Override
    public void close() {
    }

    @GET
    @Path("hello")
    @Produces(MediaType.APPLICATION_JSON)
    public Response hello() {
        return Response.ok(Collections.singletonMap("hello", session.getContext().getRealm().getName())).build();
    }

    @GET
    @Path("hello-auth")
    @Produces(MediaType.APPLICATION_JSON)
    public Response helloAuth() {
        AuthResult auth = checkAuth();
        return Response.ok(Collections.singletonMap("hello", auth.getUser().getUsername())).build();
    }

    private AuthResult checkAuth() {
        AuthResult auth = new AppAuthManager.BearerTokenAuthenticator(session).authenticate();
        if (auth == null) {
            throw new NotAuthorizedException("Bearer");
        } else if (auth.getToken().getIssuedFor() == null || !auth.getToken().getIssuedFor().equals("admin-cli")) {
            throw new ForbiddenException("Token is not properly issued for admin-cli");
        }
        return auth;
    }
}

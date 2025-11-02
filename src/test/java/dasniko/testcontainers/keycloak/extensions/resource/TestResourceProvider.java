package dasniko.testcontainers.keycloak.extensions.resource;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.Urls;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager.AuthResult;
import org.keycloak.services.resource.RealmResourceProvider;

import java.util.Collections;
import java.util.Map;

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
        return Response.ok(Collections.singletonMap("hello", auth.user().getUsername())).build();
    }

    @GET
    @Path("theme-root")
    @Produces(MediaType.APPLICATION_JSON)
    public Response themeRoot() {
        return Response.ok(Map.of("url", Urls.themeRoot(session.getContext().getUri().getBaseUri()).toString())).build();
    }

    private AuthResult checkAuth() {
        AuthResult auth = new AppAuthManager.BearerTokenAuthenticator(session).authenticate();
        if (auth == null) {
            throw new NotAuthorizedException("Bearer");
        } else if (auth.token().getIssuedFor() == null || !auth.token().getIssuedFor().equals("admin-cli")) {
            throw new ForbiddenException("Token is not properly issued for admin-cli");
        }
        return auth;
    }
}

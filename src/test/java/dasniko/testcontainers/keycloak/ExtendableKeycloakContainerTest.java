package dasniko.testcontainers.keycloak;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.utility.MountableFile;

class ExtendableKeycloakContainerTest {


    @ParameterizedTest
    @ValueSource(strings = {"target/classes", "target/test-classes", "build/classes/java/main", "build/classes/java/test"})
    void resolveExtensionClassLocation(final String input){

        final Path expected = absolute(input);

        resolveAndAssert(input, expected);

    }


    private static void resolveAndAssert(final String input, final Path expected) {
        try(final KeycloakContainer keycloakContainer = new KeycloakContainer()){
            final String resolvedExtensionClassLocation = keycloakContainer.resolveExtensionClassLocation(input);
            Assertions.assertEquals(expected.toString(), resolvedExtensionClassLocation);
        }
    }

    private static Path absolute(final String expected) {
        return Paths.get(MountableFile.forClasspathResource(".").getResolvedPath())
                .getParent()
                .getParent()
                .resolve(expected)
                .toAbsolutePath();
    }
}
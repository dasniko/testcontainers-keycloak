package dasniko.testcontainers.keycloak;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.MountableFile;

class ExtendableKeycloakContainerTest {


    @DisplayName("`resolveExtensionClassLocation` resolves `target/classes`")
    @Test
    void resolveExtensionClassLocationResolvesTargetClasses(){
        final String input = "target/classes";
        final Path expected = absolute("target/classes");

        resolveAndAssert(input, expected);
    }

    @DisplayName("`resolveExtensionClassLocation` resolves `target/test-classes`")
    @Test
    void resolveExtensionClassLocationResolvesTargetTestClasses(){
        final String input = "target/test-classes";
        final Path expected = absolute("target/test-classes");

        resolveAndAssert(input, expected);
    }

    @DisplayName("`resolveExtensionClassLocation` resolves `build/classes/java/main`")
    @Test
    void resolveExtensionClassLocationResolvesBuildClassesMain(){
        final String input = "build/classes/java/main";
        final Path expected = absolute("build/classes/java/main");

        resolveAndAssert(input, expected);
    }

    @DisplayName("`resolveExtensionClassLocation` resolves `build/classes/java/test`")
    @Test
    void resolveExtensionClassLocationResolvesBuildClassesTest(){
        final String input = "build/classes/java/test";
        final Path expected = absolute("build/classes/java/test");

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
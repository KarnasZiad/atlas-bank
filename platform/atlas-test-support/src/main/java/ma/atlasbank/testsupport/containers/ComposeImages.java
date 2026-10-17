package ma.atlasbank.testsupport.containers;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.testcontainers.utility.DockerImageName;
import org.yaml.snakeyaml.Yaml;

/**
 * Container images of the runtime stack, read from {@code compose.yaml}: integration tests run the
 * very versions that are deployed, and a version bump (by hand or by Dependabot) applies to both.
 */
public final class ComposeImages {

    private static final String COMPOSE_FILE = "compose.yaml";
    private static final Map<String, Map<String, Object>> SERVICES = loadServices();

    private ComposeImages() {
    }

    /**
     * @param service name of the service in {@code compose.yaml}, for instance {@code postgres}
     */
    public static DockerImageName of(String service) {
        Map<String, Object> definition = SERVICES.get(service);
        if (definition == null || !(definition.get("image") instanceof String image)) {
            throw new IllegalArgumentException("No image declared for service '%s' in %s".formatted(service, COMPOSE_FILE));
        }
        return DockerImageName.parse(image);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> loadServices() {
        Path compose = locate(Path.of("").toAbsolutePath());
        try (Reader reader = Files.newBufferedReader(compose)) {
            Map<String, Object> root = new Yaml().load(reader);
            return (Map<String, Map<String, Object>>) root.get("services");
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot read " + compose, unreadable);
        }
    }

    /** Tests run from a module directory: the compose file sits at the root of the repository. */
    private static Path locate(Path start) {
        for (Path directory = start; directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve(COMPOSE_FILE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(COMPOSE_FILE + " not found in " + start + " or its parents");
    }
}

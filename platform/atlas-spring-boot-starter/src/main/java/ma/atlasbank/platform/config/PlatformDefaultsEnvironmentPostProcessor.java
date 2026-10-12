package ma.atlasbank.platform.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Contributes the platform-wide configuration shared by every service (security, Kafka,
 * JPA, actuator, logging...) as the <em>lowest</em> precedence property source.
 *
 * <p>This replaces a configuration server for shared defaults: the conventions travel with
 * the starter version, while each service's {@code application.yml} and the environment
 * variables of the deployment can still override any of them.
 */
public class PlatformDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "atlasPlatformDefaults";

    private static final String DEFAULTS_LOCATION = "atlas/platform-defaults.yml";

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Resource defaults = new ClassPathResource(DEFAULTS_LOCATION, getClass().getClassLoader());
        for (PropertySource<?> source : load(defaults)) {
            environment.getPropertySources().addLast(source);
        }
    }

    private List<PropertySource<?>> load(Resource resource) {
        try {
            return loader.load(PROPERTY_SOURCE_NAME, resource);
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to load platform defaults from " + resource, ex);
        }
    }

    /**
     * Runs after config data processing so that {@code addLast} places the defaults behind
     * {@code application.yml}, environment variables and command-line arguments.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

package ma.atlasbank.testsupport.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * Dependency rules every service must respect. Each service runs them from a one-line test,
 * so the architecture is verified by the build rather than described in a wiki.
 *
 * <pre>
 *   web ─────────┐
 *                ├──► application ──► domain
 *   infrastructure ┘
 * </pre>
 */
public final class HexagonalArchitectureRules {

    private static final String DOMAIN = "Domain";
    private static final String APPLICATION = "Application";
    private static final String INFRASTRUCTURE = "Infrastructure";
    private static final String WEB = "Web";

    private HexagonalArchitectureRules() {
    }

    public static JavaClasses importProductionClasses(String basePackage) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
    }

    public static List<ArchRule> rulesFor(String basePackage) {
        return List.of(
                layersRule(basePackage),
                domainIsFrameworkFree(basePackage),
                applicationIgnoresDeliveryMechanisms(basePackage));
    }

    /**
     * Dependencies only point inwards: adapters depend on the core, never the reverse.
     */
    static ArchRule layersRule(String basePackage) {
        return layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer(DOMAIN).definedBy(basePackage + ".domain..")
                .layer(APPLICATION).definedBy(basePackage + ".application..")
                .layer(INFRASTRUCTURE).definedBy(basePackage + ".infrastructure..")
                .layer(WEB).definedBy(basePackage + ".web..")
                .whereLayer(WEB).mayNotBeAccessedByAnyLayer()
                .whereLayer(INFRASTRUCTURE).mayNotBeAccessedByAnyLayer()
                .whereLayer(APPLICATION).mayOnlyBeAccessedByLayers(WEB, INFRASTRUCTURE)
                .whereLayer(DOMAIN).mayOnlyBeAccessedByLayers(APPLICATION, WEB, INFRASTRUCTURE)
                .because("the business core must not know how it is exposed or persisted");
    }

    /**
     * The domain model may carry persistence mapping metadata (pragmatic hexagonal architecture),
     * but never depends on Spring, web, messaging or security frameworks.
     */
    static ArchRule domainIsFrameworkFree(String basePackage) {
        return noClasses().that().resideInAPackage(basePackage + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "org.apache.kafka..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..")
                .because("business rules must stay testable and portable without any framework");
    }

    /**
     * Use cases are triggered by HTTP and Kafka adapters but never know about them.
     */
    static ArchRule applicationIgnoresDeliveryMechanisms(String basePackage) {
        return noClasses().that().resideInAPackage(basePackage + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.http..",
                        "org.springframework.kafka..",
                        "org.springframework.security..",
                        "jakarta.servlet..",
                        "org.apache.kafka..")
                .because("use cases are independent from the delivery mechanism");
    }
}

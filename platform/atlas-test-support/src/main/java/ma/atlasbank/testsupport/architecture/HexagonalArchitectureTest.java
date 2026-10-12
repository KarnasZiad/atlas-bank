package ma.atlasbank.testsupport.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Base class of each service's architecture test: one dynamic test per rule.
 */
public abstract class HexagonalArchitectureTest {

    private final String basePackage;

    protected HexagonalArchitectureTest(String basePackage) {
        this.basePackage = basePackage;
    }

    @TestFactory
    Stream<DynamicTest> respectsTheHexagonalArchitecture() {
        JavaClasses classes = HexagonalArchitectureRules.importProductionClasses(basePackage);
        return HexagonalArchitectureRules.rulesFor(basePackage).stream()
                .map(rule -> DynamicTest.dynamicTest(rule.getDescription(), () -> rule.check(classes)));
    }
}

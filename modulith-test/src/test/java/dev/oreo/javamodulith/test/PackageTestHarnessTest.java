package dev.oreo.javamodulith.test;

import dev.oreo.javamodulith.core.ModuleDependencyException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackageTestHarnessTest {
    @Test void onlyStartsTargetPackageAndItsTransitiveDependencies() {
        try (var harness = ModuleTestHarness.builder()
                .basePackage("dev.oreo.javamodulith.testfixture")
                .target("orders")
                .start()) {
            harness.assertRunning("billing")
                   .assertRunning("orders")
                   .assertStartupOrder("billing", "orders");
            assertEquals(List.of("billing", "orders"),
                    harness.runtime().modules().stream().map(m -> m.id()).toList());
            assertFalse(harness.runtime().states().containsKey("audit"));
        }
    }

    @Test void rejectsUnknownTarget() {
        assertThrows(ModuleDependencyException.class,
                () -> ModuleTestHarness.builder()
                    .basePackage("dev.oreo.javamodulith.testfixture")
                    .target("missing")
                    .start());
    }
}

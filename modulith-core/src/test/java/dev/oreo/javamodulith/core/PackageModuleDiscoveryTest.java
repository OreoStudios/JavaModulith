package dev.oreo.javamodulith.core;

import dev.oreo.javamodulith.fixture.audit.AuditListener;
import dev.oreo.javamodulith.fixture.billing.payments.BillingService;
import dev.oreo.javamodulith.fixture.orders.OrderModule;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackageModuleDiscoveryTest {
    private static final String ROOT = "dev.oreo.javamodulith.fixture";

    @Test void discoversDirectChildPackagesIncludingUnannotatedAndNoLifecycleModules() {
        var modules = PackageModuleDiscovery.discover(ROOT);
        assertEquals(List.of("audit", "billing", "empty", "orders", "utilities"),
                modules.stream().map(d -> d.descriptor().id()).toList());

        var orders = modules.stream()
                .filter(d -> d.descriptor().id().equals("orders"))
                .findFirst().orElseThrow();
        assertEquals("dev.oreo.javamodulith.fixture.orders", orders.packageName());
        assertEquals(List.of("billing::payments"), orders.descriptor().dependencies());
        assertEquals(OrderModule.class, orders.descriptor().implementation());

        var empty = modules.stream()
                .filter(d -> d.descriptor().id().equals("empty")).findFirst().orElseThrow();
        assertEquals(PackageModuleDiscovery.EmptyModule.class, empty.descriptor().implementation());
    }

    @Test void packageBootstrapResolvesNamedInterfaceWithoutPerClassRegistration() {
        AuditListener.observed.set(0);
        try (var runtime = ModuleRuntime.builder().basePackage(ROOT).start()) {
            var ids = runtime.modules().stream().map(ModuleDescriptor::id).toList();
            assertTrue(ids.indexOf("billing") < ids.indexOf("orders"),
                    "billing must start before orders regardless of scan order");
            assertEquals(101, OrderModule.latestBalance);
            assertEquals(1, AuditListener.observed.get());
            assertEquals(ModuleState.RUNNING, runtime.states().get("utilities"));
            assertEquals(ModuleState.RUNNING, runtime.states().get("empty"));
            assertTrue(runtime.graphMermaid().contains("payments"));
            assertEquals(1, runtime.diagnostics().serviceCount());
        }
    }

    @Test void rootInterfaceIsPublicButInternalSubpackageIsNotByDefault() {
        var registry = new java.util.concurrent.ConcurrentHashMap<Class<?>, ModuleServices.ServiceEntry>();
        ModuleServices owner = new ModuleServices("billing", List.of(), ROOT + ".billing", registry);
        owner.publish(BillingService.class, () -> 5);
        ModuleServices allowed = new ModuleServices("orders",
                List.of(new ModuleDependency("billing", "payments")), ROOT + ".orders", registry);
        assertEquals(5, allowed.require(BillingService.class).balance());

        ModuleServices blocked = new ModuleServices("other", List.of(), ROOT + ".other", registry);
        assertThrows(ModuleDependencyException.class, () -> blocked.require(BillingService.class));

        assertThrows(ModulithException.class,
                () -> owner.publish(dev.oreo.javamodulith.fixture.billing.internal.HiddenService.class,
                        () -> { }));
    }

    @Test void scanAndBasePackageAliasesBothWork() {
        assertEquals(
                ModuleRuntime.builder().scan(ROOT).build().modules().stream().map(ModuleDescriptor::id).toList(),
                ModuleRuntime.builder().basePackage(ROOT).build().modules().stream().map(ModuleDescriptor::id).toList()
        );
    }
}

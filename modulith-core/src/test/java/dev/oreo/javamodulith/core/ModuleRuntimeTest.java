package dev.oreo.javamodulith.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class ModuleRuntimeTest {
    @ModuleApi("payments") public interface Billing { int balance(); }
    @ApplicationModule("billing") public static class BillingModule implements ModulithModule,Billing {
        public void start(ModuleContext ctx) { ctx.services().publish(Billing.class,this); }
        public int balance() { return 42; }
    }
    @ApplicationModule(value="orders",dependencies="billing::payments")
    public static class OrdersModule implements ModulithModule {
        static int observed;
        public void start(ModuleContext ctx) { observed=ctx.services().require(Billing.class).balance(); }
    }
    @ApplicationModule("no-access") public static class NoAccess implements ModulithModule {
        public void start(ModuleContext ctx) { ctx.services().require(Billing.class); }
    }
    @ApplicationModule(value="cycle-a",dependencies="cycle-b") public static class CycleA implements ModulithModule { }
    @ApplicationModule(value="cycle-b",dependencies="cycle-a") public static class CycleB implements ModulithModule { }
    @ApplicationModule("fails") public static class Fails implements ModulithModule {
        static AtomicInteger cleanup = new AtomicInteger();
        public void start(ModuleContext ctx) { ctx.lifecycle().onClose(cleanup::incrementAndGet); throw new IllegalStateException("expected"); }
    }
    @Test void orderAndNamedDependency() {
        try (var runtime=ModuleRuntime.builder().module(OrdersModule.class).module(BillingModule.class).start()) {
            assertEquals(List.of("billing","orders"),runtime.modules().stream().map(ModuleDescriptor::id).toList());
            assertEquals(42,OrdersModule.observed);
            assertEquals(1,runtime.diagnostics().serviceCount());
            assertEquals(2,runtime.states().size());
        }
    }
    @Test void rejectsUndeclaredService() {
        assertThrows(ModulithException.class, () -> ModuleRuntime.builder().module(BillingModule.class).module(NoAccess.class).start());
    }
    @Test void rejectsCycles() {
        assertThrows(ModuleDependencyException.class, () -> ModuleRuntime.builder().module(CycleA.class).module(CycleB.class).build());
    }
    @Test void startFailureReleasesScope() {
        Fails.cleanup.set(0);
        assertThrows(ModulithException.class, () -> ModuleRuntime.builder().module(Fails.class).start());
        assertEquals(1,Fails.cleanup.get());
    }
    @Test void cannotRestartStoppedRuntime() {
        ModuleRuntime runtime=ModuleRuntime.builder().module(BillingModule.class).start();
        runtime.close();
        assertThrows(ModulithException.class,runtime::start);
    }
}

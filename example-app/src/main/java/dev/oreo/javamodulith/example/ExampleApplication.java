package dev.oreo.javamodulith.example;
import dev.oreo.javamodulith.core.*;
public final class ExampleApplication {
    public static void main(String[] args) {
        try (ModuleRuntime runtime=ModuleRuntime.builder()
                .module(BillingModule.class)
                .module(AuditModule.class)
                .module(OrdersModule.class)
                .start()) {
            runtime.events().publish(new OrderPlaced("bob", 15));
            System.out.println(runtime.graphMermaid());
            System.out.println(runtime.diagnostics());
        }
    }
}

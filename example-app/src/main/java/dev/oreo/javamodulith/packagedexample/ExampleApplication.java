package dev.oreo.javamodulith.packagedexample;

import dev.oreo.javamodulith.core.ModuleRuntime;
import dev.oreo.javamodulith.packagedexample.orders.OrderPlaced;

/**
 * Spring-style package modules: discovers all direct subpackages by convention.
 * The module annotation belongs in package-info.java, not on the lifecycle class.
 */
public final class ExampleApplication {
    public static void main(String[] args) {
        try (var runtime = ModuleRuntime.builder()
                .basePackage("dev.oreo.javamodulith.packagedexample")
                .start()) {
            runtime.events().publish(new OrderPlaced("bob", 15));
            System.out.println(runtime.graphMermaid());
            System.out.println(runtime.diagnostics());
        }
    }
}

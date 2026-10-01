package dev.oreo.javamodulith.core;

import java.util.List;
import java.util.Objects;

/** Immutable description of a JavaModulith package or legacy type module. */
public record ModuleDescriptor(
        String id, List<String> dependencies,
        Class<? extends ModulithModule> implementation, String modulePackage
) {
    public ModuleDescriptor {
        Objects.requireNonNull(id);
        Objects.requireNonNull(dependencies);
        Objects.requireNonNull(implementation);
        Objects.requireNonNull(modulePackage);
        dependencies = List.copyOf(dependencies);
    }

    /** Backward-compatible constructor for explicitly registered class modules. */
    public ModuleDescriptor(String id, List<String> dependencies, Class<? extends ModulithModule> implementation) {
        this(id, dependencies, implementation, implementation.getPackageName());
    }

    public List<ModuleDependency> parsedDependencies() {
        return dependencies.stream().map(ModuleDependency::parse).toList();
    }
}

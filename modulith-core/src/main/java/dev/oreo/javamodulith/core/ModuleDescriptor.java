package dev.oreo.javamodulith.core;
import java.util.List;
import java.util.Objects;
public record ModuleDescriptor(String id, List<String> dependencies, Class<? extends ModulithModule> implementation) {
    public ModuleDescriptor {
        Objects.requireNonNull(id); Objects.requireNonNull(dependencies); Objects.requireNonNull(implementation);
        dependencies = List.copyOf(dependencies);
    }
    public List<ModuleDependency> parsedDependencies() {
        return dependencies.stream().map(ModuleDependency::parse).toList();
    }
}

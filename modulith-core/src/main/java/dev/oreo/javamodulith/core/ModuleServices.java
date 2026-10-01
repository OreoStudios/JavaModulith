package dev.oreo.javamodulith.core;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Access-controlled service registry facade for the owning module. */
public final class ModuleServices {
    private final String moduleId;
    private final List<ModuleDependency> allowed;
    private final String modulePackage;
    private final ConcurrentHashMap<Class<?>, ServiceEntry> shared;

    ModuleServices(String moduleId, List<ModuleDependency> allowed, String modulePackage,
                   ConcurrentHashMap<Class<?>, ServiceEntry> shared) {
        this.moduleId = moduleId;
        this.allowed = List.copyOf(allowed);
        this.modulePackage = modulePackage;
        this.shared = shared;
    }

    public <T> void publish(Class<T> type, T implementation) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(implementation);
        if (!type.isInstance(implementation))
            throw new IllegalArgumentException("Service implementation does not match " + type);
        if (!type.isInterface() || !Modifier.isPublic(type.getModifiers()))
            throw new ModulithException("Published contract must be a public interface: " + type.getName());

        ModuleApi legacy = type.getAnnotation(ModuleApi.class);
        NamedInterface named = type.getAnnotation(NamedInterface.class);
        if (named == null) named = type.getPackage().getAnnotation(NamedInterface.class);

        String apiName;
        if (named != null) apiName = named.value();
        else if (legacy != null) apiName = legacy.value();
        else if (type.getPackageName().equals(modulePackage)) apiName = "";
        else throw new ModulithException("Only module-root APIs or @NamedInterface public contracts may be published: "
                + type.getName() + " (module root: " + modulePackage + ")");

        if (named != null && apiName.isBlank())
            throw new ModulithException("@NamedInterface name must not be blank: " + type.getName());
        if (legacy != null && legacy.value().isBlank())
            throw new ModulithException("@ModuleApi name must not be blank: " + type.getName());

        if (shared.putIfAbsent(type, new ServiceEntry(moduleId, apiName, implementation)) != null)
            throw new ModulithException("Service already published: " + type.getName());
    }

    public <T> T require(Class<T> type) {
        Objects.requireNonNull(type);
        ServiceEntry entry = shared.get(type);
        if (entry == null) throw new ModulithException("Missing service: " + type.getName());
        if (!entry.owner().equals(moduleId)
                && allowed.stream().noneMatch(d -> d.allows(entry.owner(), entry.apiName())))
            throw new ModuleDependencyException("Module '" + moduleId
                    + "' may not access '" + entry.owner()
                    + (entry.apiName().isBlank() ? "'" : "::" + entry.apiName() + "'"));
        return type.cast(entry.service());
    }

    static record ServiceEntry(String owner, String apiName, Object service) { }
}

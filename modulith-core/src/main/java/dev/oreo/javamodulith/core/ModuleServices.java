package dev.oreo.javamodulith.core;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** Access-controlled service registry facade for the owning module. */
public final class ModuleServices {
    private final String moduleId;
    private final List<ModuleDependency> allowed;
    private final ConcurrentHashMap<Class<?>, ServiceEntry> shared;
    ModuleServices(String moduleId, List<ModuleDependency> allowed, ConcurrentHashMap<Class<?>, ServiceEntry> shared) {
        this.moduleId = moduleId; this.allowed = List.copyOf(allowed); this.shared = shared;
    }
    public <T> void publish(Class<T> type, T implementation) {
        Objects.requireNonNull(type); Objects.requireNonNull(implementation);
        if (!type.isInstance(implementation)) throw new IllegalArgumentException("Service implementation does not match " + type);
        ModuleApi api = type.getAnnotation(ModuleApi.class);
        if (api == null || api.value().isBlank() || !type.isInterface() || !java.lang.reflect.Modifier.isPublic(type.getModifiers()))
            throw new ModulithException("Published service requires public interface with @ModuleApi: " + type.getName());
        if (shared.putIfAbsent(type, new ServiceEntry(moduleId, api.value(), implementation)) != null)
            throw new ModulithException("Service already published: " + type.getName());
    }
    public <T> T require(Class<T> type) {
        Objects.requireNonNull(type);
        ServiceEntry entry = shared.get(type);
        if (entry == null) throw new ModulithException("Missing service: " + type.getName());
        if (!entry.owner().equals(moduleId) && allowed.stream().noneMatch(d -> d.allows(entry.owner(), entry.apiName())))
            throw new ModuleDependencyException("Module '"+ moduleId +"' may not access '" + entry.owner() + "::"+entry.apiName()+"'");
        return type.cast(entry.service());
    }
    static record ServiceEntry(String owner, String apiName, Object service) { }
}

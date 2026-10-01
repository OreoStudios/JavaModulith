package dev.oreo.javamodulith.core;
import java.util.Objects;
/** Matches a full module (billing) or named contract (billing::payments). */
public record ModuleDependency(String moduleId, String apiName) {
    public ModuleDependency {
        Objects.requireNonNull(moduleId, "moduleId");
        if (moduleId.isBlank() || !moduleId.matches("[a-zA-Z][a-zA-Z0-9_.-]*"))
            throw new IllegalArgumentException("Invalid module id: " + moduleId);
        if (apiName != null && (apiName.isBlank() || !apiName.matches("[a-zA-Z][a-zA-Z0-9_.-]*")))
            throw new IllegalArgumentException("Invalid API name: " + apiName);
    }
    public static ModuleDependency parse(String selector) {
        Objects.requireNonNull(selector, "selector");
        String[] parts = selector.split("::", -1);
        if (parts.length > 2) throw new IllegalArgumentException("Invalid dependency: " + selector);
        return new ModuleDependency(parts[0], parts.length == 2 ? parts[1] : null);
    }
    public boolean allows(String owner, String api) {
        return moduleId.equals(owner) && (apiName == null || apiName.equals(api));
    }
}

package dev.oreo.javamodulith.core;
import java.util.*;
/** Validates dependencies and produces stable topological order. */
public final class ModuleGraph {
    private ModuleGraph() {}
    public static List<ModuleDescriptor> validateAndSort(Collection<ModuleDescriptor> modules) {
        Map<String, ModuleDescriptor> byId = new LinkedHashMap<>();
        for (ModuleDescriptor module : modules) {
            if (!module.id().matches("[a-zA-Z][a-zA-Z0-9_.-]*"))
                throw new ModuleDependencyException("Invalid module id: " + module.id());
            if (byId.putIfAbsent(module.id(), module) != null)
                throw new ModuleDependencyException("Duplicate module id: " + module.id());
        }
        for (ModuleDescriptor module : byId.values()) {
            for (ModuleDependency dep : module.parsedDependencies()) {
                if (dep.moduleId().equals(module.id()))
                    throw new ModuleDependencyException("Module cannot depend on itself: " + module.id());
                if (!byId.containsKey(dep.moduleId()))
                    throw new ModuleDependencyException("Missing dependency '" + dep.moduleId() + "' for '" + module.id() + "'");
            }
        }
        Map<String, Integer> states = new HashMap<>();
        Deque<String> path = new ArrayDeque<>();
        List<ModuleDescriptor> order = new ArrayList<>();
        for (ModuleDescriptor module : byId.values()) visit(module, byId, states, path, order);
        return List.copyOf(order);
    }
    private static void visit(ModuleDescriptor module, Map<String, ModuleDescriptor> byId,
                              Map<String, Integer> states, Deque<String> path, List<ModuleDescriptor> order) {
        int state = states.getOrDefault(module.id(), 0);
        if (state == 2) return;
        if (state == 1) throw new ModuleDependencyException("Circular dependency at '" + module.id() + "', path=" + path);
        states.put(module.id(), 1);
        path.push(module.id());
        for (ModuleDependency dep : module.parsedDependencies()) visit(byId.get(dep.moduleId()), byId, states, path, order);
        path.pop();
        states.put(module.id(), 2);
        order.add(module);
    }
}

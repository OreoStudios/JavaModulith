package dev.oreo.javamodulith.core;
import java.util.*;
/** Graph exporters for architecture documentation. */
public final class ModuleGraphExporter {
    private ModuleGraphExporter() {}
    private static String node(String id) { return "m_" + id.replaceAll("[^a-zA-Z0-9_]", "_"); }
    private static String label(String input) { return input.replace("\\", "\\\\").replace("\"", "\\\""); }
    public static String mermaid(List<ModuleDescriptor> modules) {
        StringBuilder result = new StringBuilder("graph TD\n");
        for (ModuleDescriptor module : modules) result.append("  ").append(node(module.id())).append("[\"").append(label(module.id())).append("\"]\n");
        for (ModuleDescriptor module : modules) for (ModuleDependency dep : module.parsedDependencies())
            result.append("  ").append(node(module.id())).append(" -->").append(dep.apiName() == null ? "" : "|"+label(dep.apiName())+"|")
                .append(" ").append(node(dep.moduleId())).append("\n");
        return result.toString();
    }
    public static String graphviz(List<ModuleDescriptor> modules) {
        StringBuilder result = new StringBuilder("digraph modulith {\n");
        for (ModuleDescriptor module : modules) result.append("  ").append(node(module.id())).append(" [label=\"").append(label(module.id())).append("\"];\n");
        for (ModuleDescriptor module : modules) for (ModuleDependency dep : module.parsedDependencies())
            result.append("  ").append(node(module.id())).append(" -> ").append(node(dep.moduleId()))
                .append(dep.apiName() == null ? "" : " [label=\""+label(dep.apiName())+"\"]").append(";\n");
        return result.append("}\n").toString();
    }
}

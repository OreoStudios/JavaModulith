package dev.oreo.javamodulith.test;
import dev.oreo.javamodulith.core.*;
import java.util.*;
public final class ModuleAssertions {
    private ModuleAssertions() { }
    public static void assertValidArchitecture(Collection<ModuleDescriptor> descriptors) {
        ModuleGraph.validateAndSort(descriptors);
    }
    public static void assertMermaidContains(ModuleRuntime runtime,String node) {
        if (!runtime.graphMermaid().contains(node)) throw new AssertionError("Missing module graph text: "+node);
    }
}

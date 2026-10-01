package dev.oreo.javamodulith.test;
import dev.oreo.javamodulith.core.*;
import java.util.*;
/** Builds a target module together with its required transitive dependencies. */
public final class ModuleTestHarness implements AutoCloseable {
    private final ModuleRuntime runtime;
    private ModuleTestHarness(ModuleRuntime runtime) { this.runtime=runtime; }
    public static Builder builder() { return new Builder(); }
    public ModuleRuntime runtime() { return runtime; }
    public ModuleTestHarness assertRunning(String id) {
        if (runtime.states().get(id)!=ModuleState.RUNNING) throw new AssertionError("Module not running: "+id);
        return this;
    }
    public ModuleTestHarness assertStartupOrder(String... ids) {
        List<String> actual=runtime.modules().stream().map(ModuleDescriptor::id).toList();
        if(!actual.equals(List.of(ids))) throw new AssertionError("Expected "+List.of(ids)+" but got "+actual);
        return this;
    }
    @Override public void close() { runtime.close(); }
    public static final class Builder {
        private final LinkedHashMap<String,Class<? extends ModulithModule>> classes=new LinkedHashMap<>();
        private String target;
        public Builder modules(Collection<Class<? extends ModulithModule>> input) {
            for (Class<? extends ModulithModule> type : input) {
                ApplicationModule annotation=type.getAnnotation(ApplicationModule.class);
                if(annotation==null) throw new ModulithException("Missing @ApplicationModule: "+type.getName());
                if(classes.putIfAbsent(annotation.value(),type)!=null) throw new ModulithException("Duplicate module ID: "+annotation.value());
            }
            return this;
        }
        public Builder target(String target) { this.target=Objects.requireNonNull(target); return this; }
        public ModuleTestHarness start() {
            if(!classes.containsKey(target)) throw new IllegalArgumentException("Missing target: "+target);
            LinkedHashSet<String> needed=new LinkedHashSet<>();
            collect(target,needed);
            ModuleRuntime.Builder builder=ModuleRuntime.builder();
            for(String id:needed) builder.module(classes.get(id));
            return new ModuleTestHarness(builder.start());
        }
        private void collect(String id,Set<String> needed) {
            if(!needed.add(id)) return;
            Class<? extends ModulithModule> type=classes.get(id);
            if(type==null) throw new ModulithException("Unknown dependency: "+id);
            for(String dependency:type.getAnnotation(ApplicationModule.class).dependencies())
                collect(ModuleDependency.parse(dependency).moduleId(),needed);
        }
    }
}

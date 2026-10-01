package dev.oreo.javamodulith.core;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.*;
/** Platform-independent application orchestrator. Explicit module registration, deterministic lifecycle. */
public final class ModuleRuntime implements AutoCloseable {
    private final List<ModuleDescriptor> order;
    private final Map<String, Supplier<? extends ModulithModule>> factories;
    private final Map<Class<?>, Object> external;
    private final Logger logger;
    private final EventJournal journal;
    private final EventBus events;
    private final ConcurrentHashMap<Class<?>, ModuleServices.ServiceEntry> services = new ConcurrentHashMap<>();
    private final LinkedHashMap<String, Running> running = new LinkedHashMap<>();
    private final LinkedHashMap<String, ModuleState> states = new LinkedHashMap<>();
    private boolean started;
    private boolean stopped;

    private ModuleRuntime(List<ModuleDescriptor> order,
        Map<String, Supplier<? extends ModulithModule>> factories,
        Map<Class<?>, Object> external, Logger logger, Executor executor, EventJournal journal) {
        this.order=order; this.factories=Map.copyOf(factories); this.external=Map.copyOf(external);
        this.logger=logger; this.journal=journal; this.events=new EventBus(executor,journal);
        order.forEach(d -> states.put(d.id(), ModuleState.DISCOVERED));
    }
    public static Builder builder() { return new Builder(); }
    public synchronized ModuleRuntime start() {
        if (started) return this;
        if (stopped) throw new ModulithException("ModuleRuntime cannot restart after stop(); build a new instance");
        try {
            for (ModuleDescriptor descriptor : order) startOne(descriptor);
            started=true;
            return this;
        } catch (RuntimeException exception) {
            try { stopStartedModules(); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            stopped=true;
            throw exception;
        }
    }
    private void startOne(ModuleDescriptor descriptor) {
        states.put(descriptor.id(), ModuleState.STARTING);
        LifecycleScope scope = new LifecycleScope();
        ModuleServices moduleServices = new ModuleServices(descriptor.id(), descriptor.parsedDependencies(), descriptor.modulePackage(), services);
        ModuleContext context = new ModuleContext(descriptor.id(), moduleServices, events, scope, external,
            Logger.getLogger(logger.getName()+"."+descriptor.id()));
        try {
            ModulithModule instance = Objects.requireNonNull(factories.get(descriptor.id()).get(), "module factory returned null");
            // Ensure services, scope, and listener registrations are rolled back if start() fails.
            instance.start(context);
            context.listen(instance);
            running.put(descriptor.id(), new Running(descriptor, instance, scope));
            states.put(descriptor.id(), ModuleState.RUNNING);
            logger.info(() -> "[JavaModulith] Started " + descriptor.id());
        } catch (Exception exception) {
            states.put(descriptor.id(), ModuleState.FAILED);
            try { scope.close(); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            services.entrySet().removeIf(entry -> entry.getValue().owner().equals(descriptor.id()));
            throw new ModulithException("Failed to start module: " + descriptor.id(), exception);
        }
    }
    public synchronized void stop() {
        if (stopped) return;
        stopped=true;
        started=false;
        stopStartedModules();
    }
    private void stopStartedModules() {
        List<Running> reverse = new ArrayList<>(running.values());
        Collections.reverse(reverse);
        RuntimeException failure=null;
        for (Running one : reverse) {
            String id=one.descriptor().id();
            states.put(id,ModuleState.STOPPING);
            try { one.instance().stop(); }
            catch (Exception exception) {
                logger.log(Level.WARNING, "Error stopping " + id, exception);
                RuntimeException wrapped = new ModulithException("Failed to stop " + id,exception);
                if (failure == null) failure=wrapped; else failure.addSuppressed(wrapped);
            }
            try { one.scope().close(); }
            catch (RuntimeException exception) {
                if (failure == null) failure=exception; else failure.addSuppressed(exception);
            }
            states.put(id,ModuleState.STOPPED);
        }
        running.clear(); services.clear();
        if (failure != null) throw failure;
    }
    public synchronized Map<String, ModuleState> states() { return Map.copyOf(states); }
    public List<ModuleDescriptor> modules() { return order; }
    public EventBus events() { return events; }
    public RuntimeDiagnostics diagnostics() {
        return new RuntimeDiagnostics(states(),order.stream().map(ModuleDescriptor::id).toList(),services.size(),
            events.eventsPublished(),events.handlersCompleted(),events.handlersFailed(),journal.incompleteCount());
    }
    public String graphMermaid() { return ModuleGraphExporter.mermaid(order); }
    public String graphGraphviz() { return ModuleGraphExporter.graphviz(order); }
    @Override public void close() { stop(); }
    private record Running(ModuleDescriptor descriptor,ModulithModule instance,LifecycleScope scope) { }

    public static final class Builder {
        private final LinkedHashMap<Class<? extends ModulithModule>,Supplier<? extends ModulithModule>> factories = new LinkedHashMap<>();
        private final LinkedHashMap<String,PackageModuleDiscovery.DiscoveredModule> discovered = new LinkedHashMap<>();
        private final Map<Class<?>,Object> external = new LinkedHashMap<>();
        private Logger logger=Logger.getLogger("JavaModulith");
        private Executor executor=ForkJoinPool.commonPool();
        private EventJournal journal=EventJournal.noop();
        public Builder module(Class<? extends ModulithModule> type) { return module(type, () -> create(type)); }
        public Builder module(Class<? extends ModulithModule> type, Supplier<? extends ModulithModule> factory) {
            Objects.requireNonNull(type); Objects.requireNonNull(factory);
            if (factories.putIfAbsent(type,factory)!=null) throw new ModulithException("Module registered twice: "+type.getName());
            return this;
        }
        public Builder modules(Collection<Class<? extends ModulithModule>> types) { types.forEach(this::module); return this; }

        /**
         * Discover direct child packages of the application base package, as in
         * Spring Modulith. Package metadata belongs in package-info.java.
         * Explicit module(Class) registration remains available for migration.
         */
        public Builder basePackage(String packageName) {
            for (var module : PackageModuleDiscovery.discover(packageName)) {
                if (discovered.putIfAbsent(module.descriptor().id(), module) != null)
                    throw new ModulithException("Duplicate discovered module id: " + module.descriptor().id());
            }
            return this;
        }

        /** Alias of basePackage for users migrating from explicit registration. */
        public Builder scan(String packageName) { return basePackage(packageName); }
        public <T> Builder externalService(Class<T> type,T service) {
            Objects.requireNonNull(type); Objects.requireNonNull(service);
            if(!type.isInstance(service)) throw new IllegalArgumentException("Wrong external service implementation");
            external.put(type,service); return this;
        }
        public Builder logger(Logger logger) { this.logger=Objects.requireNonNull(logger); return this; }
        public Builder eventExecutor(Executor executor) { this.executor=Objects.requireNonNull(executor); return this; }
        public Builder eventJournal(EventJournal journal) { this.journal=Objects.requireNonNull(journal); return this; }
        public ModuleRuntime build() {
            List<ModuleDescriptor> descriptors = new ArrayList<>();
            Map<String, Supplier<? extends ModulithModule>> byId = new LinkedHashMap<>();
            for (var entry : factories.entrySet()) {
                ModuleDescriptor descriptor = describe(entry.getKey());
                if (byId.putIfAbsent(descriptor.id(), entry.getValue()) != null)
                    throw new ModulithException("Duplicate module id: " + descriptor.id());
                descriptors.add(descriptor);
            }
            for (var item : discovered.values()) {
                ModuleDescriptor descriptor = item.descriptor();
                if (byId.putIfAbsent(descriptor.id(), item.factory()) != null)
                    throw new ModulithException("Duplicate module id between explicit and discovered registration: " + descriptor.id());
                descriptors.add(descriptor);
            }
            return new ModuleRuntime(ModuleGraph.validateAndSort(descriptors),byId,external,logger,executor,journal);
        }
        public ModuleRuntime start() { return build().start(); }
        private static ModuleDescriptor describe(Class<? extends ModulithModule> type) {
            ApplicationModule annotation=type.getAnnotation(ApplicationModule.class);
            if(annotation==null) throw new ModulithException("Missing @ApplicationModule: "+type.getName());
            String id = annotation.value().isBlank() ? type.getSimpleName() : annotation.value();
            return new ModuleDescriptor(id,List.of(annotation.dependencies()),type);
        }
        static ModulithModule create(Class<? extends ModulithModule> type) {
            try {
                Constructor<? extends ModulithModule> ctor=type.getDeclaredConstructor(); ctor.setAccessible(true);
                return ctor.newInstance();
            } catch (ReflectiveOperationException exception) { throw new ModulithException("Cannot instantiate "+type.getName()+"; provide a factory",exception); }
        }
    }
}

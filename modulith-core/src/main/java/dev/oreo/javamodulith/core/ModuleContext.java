package dev.oreo.javamodulith.core;
import java.util.*;
import java.util.logging.Logger;
/** Shared facilities available to an application module. */
public final class ModuleContext {
    private final String id;
    private final ModuleServices services;
    private final EventBus events;
    private final LifecycleScope lifecycle;
    private final Map<Class<?>, Object> external;
    private final Logger logger;
    ModuleContext(String id, ModuleServices services, EventBus events, LifecycleScope lifecycle,
                  Map<Class<?>, Object> external, Logger logger) {
        this.id=id; this.services=services; this.events=events; this.lifecycle=lifecycle; this.external=external; this.logger=logger;
    }
    public String moduleId() { return id; }
    public ModuleServices services() { return services; }
    public EventBus events() { return events; }
    public LifecycleScope lifecycle() { return lifecycle; }
    public Logger logger() { return logger; }
    public <T> T external(Class<T> type) {
        Object obj = external.get(Objects.requireNonNull(type));
        if (obj == null) throw new ModulithException("External service not provided: " + type.getName());
        return type.cast(obj);
    }
    /** Register a separate listener object, automatically unsubscribe when the owning module stops. */
    public void listen(Object listener) { events.register(listener).forEach(subscription -> lifecycle.onClose(subscription::close)); }
}

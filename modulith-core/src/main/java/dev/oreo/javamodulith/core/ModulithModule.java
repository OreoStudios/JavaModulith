package dev.oreo.javamodulith.core;
/** Implemented by plain Java application modules; no framework inheritance. */
public interface ModulithModule {
    default void start(ModuleContext context) throws Exception { }
    default void stop() throws Exception { }
}

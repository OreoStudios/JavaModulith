package dev.oreo.javamodulith.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional lifecycle entrypoint in a package-discovered module.
 *
 * <p>The annotated concrete class must implement ModulithModule. Without this
 * annotation, one unique concrete ModulithModule in a module is used. If none
 * exists the package is still a valid architectural module (no-op lifecycle).</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleEntrypoint { }

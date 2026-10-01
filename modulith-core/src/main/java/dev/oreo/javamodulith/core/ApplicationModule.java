package dev.oreo.javamodulith.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares metadata for an application module.
 *
 * <p>Preferred: annotate the module's root package in package-info.java.
 * Each direct subpackage of the base package is a module by convention even
 * without this annotation. Type-level usage remains supported for migration.</p>
 *
 * <p>Examples: dependencies = {"billing"} or {"billing::payments"}.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface ApplicationModule {
    /** Optional override of the package's last segment. */
    String value() default "";
    /** Spring Modulith-style dependency selectors. */
    String[] allowedDependencies() default {};
    /** Legacy alias retained for compatibility with type-based modules. */
    String[] dependencies() default {};
}

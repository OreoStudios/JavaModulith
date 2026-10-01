package dev.oreo.javamodulith.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a named public interface for a module.
 *
 * <p>Prefer annotating api/package-info.java. Consumers declare dependencies
 * on module::name (for example billing::payments). Can annotate API types too.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface NamedInterface {
    String value();
}

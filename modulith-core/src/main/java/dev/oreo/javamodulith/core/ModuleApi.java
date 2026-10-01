package dev.oreo.javamodulith.core;
import java.lang.annotation.*;
/** Marks a public service contract with a named API boundary. */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface ModuleApi { String value(); }

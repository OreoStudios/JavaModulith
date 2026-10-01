package dev.oreo.javamodulith.core;
import java.lang.annotation.*;
/** Defines one internal application module. dependencies accepts id or id::namedApi. */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface ApplicationModule {
    String value();
    String[] dependencies() default {};
}

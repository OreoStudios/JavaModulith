package dev.oreo.javamodulith.core;
import java.lang.annotation.*;
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface ModuleListener {
    String id() default "";
    EventDelivery delivery() default EventDelivery.SYNC;
}

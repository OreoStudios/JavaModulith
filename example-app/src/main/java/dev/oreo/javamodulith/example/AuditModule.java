package dev.oreo.javamodulith.example;
import dev.oreo.javamodulith.core.*;
@ApplicationModule("audit")
public final class AuditModule implements ModulithModule {
    @ModuleListener public void record(OrderPlaced event) { System.out.println("AUDIT: "+event); }
}

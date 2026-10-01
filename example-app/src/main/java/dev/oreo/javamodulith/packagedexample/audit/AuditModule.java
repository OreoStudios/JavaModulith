package dev.oreo.javamodulith.packagedexample.audit;

import dev.oreo.javamodulith.core.*;
import dev.oreo.javamodulith.packagedexample.orders.OrderPlaced;

/** No package-info required: direct child subpackages are modules by default. */
public final class AuditModule implements ModulithModule {
    @ModuleListener
    public void record(OrderPlaced event) {
        System.out.println("AUDIT: " + event);
    }
}

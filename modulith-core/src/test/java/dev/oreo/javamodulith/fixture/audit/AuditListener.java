package dev.oreo.javamodulith.fixture.audit;

import dev.oreo.javamodulith.core.*;
import dev.oreo.javamodulith.fixture.orders.OrderPlaced;
import java.util.concurrent.atomic.AtomicInteger;

public final class AuditListener implements ModulithModule {
    public static final AtomicInteger observed = new AtomicInteger();
    @ModuleListener public void record(OrderPlaced event) { observed.incrementAndGet(); }
}

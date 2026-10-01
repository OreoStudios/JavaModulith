package dev.oreo.javamodulith.fixture.billing;

import dev.oreo.javamodulith.core.*;
import dev.oreo.javamodulith.fixture.billing.payments.BillingService;

@ModuleEntrypoint
public final class BillingModule implements ModulithModule, BillingService {
    @Override public void start(ModuleContext context) {
        context.services().publish(BillingService.class, this);
    }
    @Override public int balance() { return 101; }
}

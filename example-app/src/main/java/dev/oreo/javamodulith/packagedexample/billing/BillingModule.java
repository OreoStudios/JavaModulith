package dev.oreo.javamodulith.packagedexample.billing;

import dev.oreo.javamodulith.core.*;
import dev.oreo.javamodulith.packagedexample.billing.payments.BillingService;

/** Optional lifecycle entrypoint inside the billing package; not a module declaration. */
@ModuleEntrypoint
public final class BillingModule implements ModulithModule, BillingService {
    @Override public void start(ModuleContext context) {
        context.services().publish(BillingService.class, this);
    }
    @Override public long balance(String customer) { return 100; }
}

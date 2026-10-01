package dev.oreo.javamodulith.packagedexample.orders;

import dev.oreo.javamodulith.core.*;
import dev.oreo.javamodulith.packagedexample.billing.payments.BillingService;

/** An unannotated lifecycle component; the package is the module. */
public final class OrdersModule implements ModulithModule {
    @Override public void start(ModuleContext context) {
        BillingService billing = context.services().require(BillingService.class);
        if (billing.balance("alice") >= 20) {
            context.events().publish(new OrderPlaced("alice", 20));
        }
    }
}

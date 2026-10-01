package dev.oreo.javamodulith.fixture.orders;

import dev.oreo.javamodulith.core.*;
import dev.oreo.javamodulith.fixture.billing.payments.BillingService;

@ModuleEntrypoint
public final class OrderModule implements ModulithModule {
    public static volatile int latestBalance;
    @Override public void start(ModuleContext context) {
        BillingService service = context.services().require(BillingService.class);
        latestBalance = service.balance();
        context.events().publish(new OrderPlaced(latestBalance));
    }
}

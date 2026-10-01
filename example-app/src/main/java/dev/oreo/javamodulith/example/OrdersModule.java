package dev.oreo.javamodulith.example;
import dev.oreo.javamodulith.core.*;
@ApplicationModule(value="orders",dependencies="billing::payments")
public final class OrdersModule implements ModulithModule {
    private ModuleContext context;
    @Override public void start(ModuleContext context) {
        this.context=context;
        BillingService billing=context.services().require(BillingService.class);
        if (billing.balance("alice")>=20) context.events().publish(new OrderPlaced("alice",20));
    }
}

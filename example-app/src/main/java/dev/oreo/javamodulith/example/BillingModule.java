package dev.oreo.javamodulith.example;
import dev.oreo.javamodulith.core.*;
@ApplicationModule("billing")
public final class BillingModule implements ModulithModule, BillingService {
    @Override public void start(ModuleContext context) { context.services().publish(BillingService.class,this); }
    @Override public long balance(String customer) { return 100; }
}

package dev.oreo.javamodulith.example;
import dev.oreo.javamodulith.core.ModuleApi;
@ModuleApi("payments")
public interface BillingService { long balance(String customer); }

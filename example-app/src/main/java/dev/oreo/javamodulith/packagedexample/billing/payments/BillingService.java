package dev.oreo.javamodulith.packagedexample.billing.payments;

/** Named interface of billing; visible to modules declaring billing::payments. */
public interface BillingService {
    long balance(String customer);
}

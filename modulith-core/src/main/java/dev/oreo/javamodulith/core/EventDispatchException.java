package dev.oreo.javamodulith.core;
import java.util.*;
public final class EventDispatchException extends ModulithException {
    private static final long serialVersionUID = 1L;
    private final List<Throwable> failures;
    public EventDispatchException(String message, List<Throwable> failures) {
        super(message, failures.isEmpty() ? null : failures.getFirst());
        this.failures = List.copyOf(failures);
        failures.stream().skip(1).forEach(this::addSuppressed);
    }
    public List<Throwable> failures() { return failures; }
}

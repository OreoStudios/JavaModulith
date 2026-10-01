package dev.oreo.javamodulith.core;
import java.util.*;
/** Tracks resources owned by one module and releases them in reverse order. */
public final class LifecycleScope implements AutoCloseable {
    private final Deque<Runnable> cleanup = new ArrayDeque<>();
    private boolean closed;
    public synchronized void onClose(Runnable action) {
        Objects.requireNonNull(action);
        if (closed) { action.run(); return; }
        cleanup.push(action);
    }
    public <T extends AutoCloseable> T own(T resource) {
        Objects.requireNonNull(resource);
        onClose(() -> {
            try { resource.close(); }
            catch (Exception exception) { throw new ModulithException("Module resource cleanup failed", exception); }
        });
        return resource;
    }
    @Override public void close() {
        RuntimeException failure = null;
        while (true) {
            Runnable action;
            synchronized (this) {
                action = cleanup.pollFirst();
                if (action == null) { closed = true; break; }
            }
            try { action.run(); }
            catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }
}

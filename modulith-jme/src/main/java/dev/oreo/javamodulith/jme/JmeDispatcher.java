package dev.oreo.javamodulith.jme;

import com.jme3.app.Application;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Enqueues operations for jME's update/render thread via Application.enqueue.
 * This is an event-loop dispatcher, not a background worker pool.
 * Never join a queued future from the same engine thread.
 */
public final class JmeDispatcher implements Executor {
    private final Application application;

    public JmeDispatcher(Application application) {
        this.application = Objects.requireNonNull(application, "application");
    }

    @Override
    public void execute(Runnable command) {
        application.enqueue(Objects.requireNonNull(command, "command"));
    }

    /** Completes after the action executes on the engine's main update/render thread. */
    public CompletableFuture<Void> run(Runnable action) {
        Objects.requireNonNull(action, "action");
        return submit(() -> { action.run(); return null; });
    }

    /** Enqueues the Callable and exposes the result without blocking the caller. */
    public <T> CompletableFuture<T> submit(Callable<T> action) {
        Objects.requireNonNull(action, "action");
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            execute(() -> {
                if (result.isCancelled()) return;
                try { result.complete(action.call()); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        return result;
    }
}

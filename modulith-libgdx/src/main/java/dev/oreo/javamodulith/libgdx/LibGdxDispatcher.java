package dev.oreo.javamodulith.libgdx;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Posts application work to LibGDX's render thread on a future frame.
 * The caller owns the Application and must create this after the backend starts.
 * Never block the render thread waiting for a result queued to the same thread.
 */
public final class LibGdxDispatcher implements Executor {
    private final Application application;

    public LibGdxDispatcher(Application application) {
        this.application = Objects.requireNonNull(application, "application");
    }

    /** Uses the active LibGDX application; call only after Gdx.app has been initialized. */
    public static LibGdxDispatcher current() {
        return new LibGdxDispatcher(Objects.requireNonNull(Gdx.app, "Gdx.app not initialized"));
    }

    @Override
    public void execute(Runnable command) {
        application.postRunnable(Objects.requireNonNull(command, "command"));
    }

    /** Completes after the Runnable executes on LibGDX's render thread. */
    public CompletableFuture<Void> run(Runnable action) {
        Objects.requireNonNull(action, "action");
        return submit(() -> { action.run(); return null; });
    }

    /** Runs the Callable on the render thread without blocking the caller. */
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

package dev.oreo.javamodulith.libgdx;

import com.badlogic.gdx.Application;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LibGdxDispatcherTest {
    @Test void postsToRenderThreadInsteadOfRunningInline() {
        Queue<Runnable> queue = new ArrayDeque<>();
        Application app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                new Class<?>[]{Application.class}, (proxy, method, args) -> {
                    if ("postRunnable".equals(method.getName())) {
                        queue.add((Runnable) args[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        LibGdxDispatcher dispatcher = new LibGdxDispatcher(app);
        AtomicBoolean ran = new AtomicBoolean();
        var future = dispatcher.run(() -> ran.set(true));
        assertFalse(ran.get());
        assertFalse(future.isDone());
        assertEquals(1, queue.size());
        queue.remove().run(); // simulate the next render frame
        assertTrue(ran.get());
        assertTrue(future.isDone());

        var failed = dispatcher.submit(() -> { throw new IllegalStateException("render failure"); });
        queue.remove().run();
        assertTrue(failed.isCompletedExceptionally());
    }

    @Test void canServeAsAnExecutor() {
        Queue<Runnable> queue = new ArrayDeque<>();
        Application app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                new Class<?>[]{Application.class}, (p, m, args) -> {
                    if ("postRunnable".equals(m.getName())) {
                        queue.add((Runnable) args[0]); return null;
                    }
                    throw new UnsupportedOperationException(m.getName());
                });
        LibGdxDispatcher dispatcher = new LibGdxDispatcher(app);
        AtomicBoolean ran = new AtomicBoolean();
        dispatcher.execute(() -> ran.set(true));
        assertFalse(ran.get());
        queue.remove().run();
        assertTrue(ran.get());
    }
}

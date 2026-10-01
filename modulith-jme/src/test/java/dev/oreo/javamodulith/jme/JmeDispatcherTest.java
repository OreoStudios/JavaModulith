package dev.oreo.javamodulith.jme;

import com.jme3.app.Application;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JmeDispatcherTest {
    @Test void queuesToTheEngineLoopAndCompletesWithoutBlocking() {
        Queue<Runnable> queue = new ArrayDeque<>();
        Application application = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                new Class<?>[]{Application.class}, (proxy, method, args) -> {
                    if ("enqueue".equals(method.getName()) && args[0] instanceof Runnable action) {
                        queue.add(action);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        JmeDispatcher dispatcher = new JmeDispatcher(application);
        var pending = dispatcher.submit(() -> 42);
        assertFalse(pending.isDone());
        assertEquals(1, queue.size());
        queue.remove().run(); // simulate jME's next update
        assertEquals(42, pending.join());

        var failing = dispatcher.run(() -> { throw new IllegalArgumentException("oops"); });
        queue.remove().run();
        assertTrue(failing.isCompletedExceptionally());
    }
}

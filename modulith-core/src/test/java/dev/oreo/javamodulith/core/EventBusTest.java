package dev.oreo.javamodulith.core;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class EventBusTest {
    record Message(String text) { }
    static class Receiver { final AtomicInteger count=new AtomicInteger();
        @ModuleListener public void receive(Message msg) { count.incrementAndGet(); }
    }
    @Test void dispatchAndUnsubscribe() {
        EventBus bus=new EventBus(); Receiver receiver=new Receiver();
        var subs=bus.register(receiver);
        assertEquals(1,bus.publish(new Message("a")).completedCount());
        assertEquals(1,receiver.count.get());
        subs.forEach(EventBus.Subscription::close);
        assertEquals(0,bus.publish(new Message("b")).listenerCount());
    }
    @Test void asyncDelivery() {
        EventBus bus=new EventBus(); AtomicInteger count=new AtomicInteger();
        bus.subscribe(Message.class,"async",EventDelivery.ASYNC,msg -> count.incrementAndGet());
        assertEquals(1,bus.publish(new Message("x")).completedCount());
        assertEquals(1,count.get());
    }
    @Test void failuresAreSurfaced() {
        EventBus bus=new EventBus();
        bus.subscribe(Message.class,msg -> { throw new IllegalArgumentException("bad"); });
        assertThrows(EventDispatchException.class,()->bus.publish(new Message("x")));
        assertEquals(1,bus.handlersFailed());
    }
}

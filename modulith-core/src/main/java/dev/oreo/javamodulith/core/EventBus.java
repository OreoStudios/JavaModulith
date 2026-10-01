package dev.oreo.javamodulith.core;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
/** Thread-safe in-process event bus with sync/async listeners and optional publication tracking. */
public final class EventBus {
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Handler>> handlers = new ConcurrentHashMap<>();
    private final Executor executor;
    private final EventJournal journal;
    private final AtomicLong eventsPublished = new AtomicLong();
    private final AtomicLong handlersCompleted = new AtomicLong();
    private final AtomicLong handlersFailed = new AtomicLong();
    private final AtomicInteger consumerIds = new AtomicInteger();
    public EventBus() { this(ForkJoinPool.commonPool(), EventJournal.noop()); }
    public EventBus(Executor executor, EventJournal journal) {
        this.executor = Objects.requireNonNull(executor); this.journal = Objects.requireNonNull(journal);
    }
    public <E> Subscription subscribe(Class<E> type, Consumer<? super E> consumer) {
        return subscribe(type, "consumer-" + consumerIds.incrementAndGet(), EventDelivery.SYNC, consumer);
    }
    public <E> Subscription subscribe(Class<E> type, String id, EventDelivery delivery, Consumer<? super E> consumer) {
        Objects.requireNonNull(type); Objects.requireNonNull(id); Objects.requireNonNull(delivery); Objects.requireNonNull(consumer);
        if (id.isBlank()) throw new IllegalArgumentException("Listener id cannot be blank");
        Handler handler = new Handler(id, delivery, event -> { consumer.accept(type.cast(event)); return CompletableFuture.completedFuture(null); });
        handlers.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add(handler);
        return () -> handlers.get(type).remove(handler);
    }
    public List<Subscription> register(Object listener) {
        Objects.requireNonNull(listener);
        List<Subscription> subscriptions = new ArrayList<>();
        try {
            for (Method method : listener.getClass().getDeclaredMethods()) {
                ModuleListener annotation = method.getAnnotation(ModuleListener.class);
                if (annotation == null) continue;
                if (method.getParameterCount() != 1) throw new ModulithException("@ModuleListener needs one parameter: " + method);
                if (method.getReturnType() != void.class && !CompletionStage.class.isAssignableFrom(method.getReturnType()))
                    throw new ModulithException("@ModuleListener must return void or CompletionStage: " + method);
                method.setAccessible(true);
                Class<?> type = method.getParameterTypes()[0];
                String id = annotation.id().isBlank() ? listener.getClass().getName()+"#"+method.getName()+"("+type.getName()+")" : annotation.id();
                Handler handler = new Handler(id, annotation.delivery(), event -> invoke(listener, method, event));
                handlers.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add(handler);
                subscriptions.add(() -> handlers.get(type).remove(handler));
            }
        } catch (RuntimeException exception) {
            subscriptions.forEach(Subscription::close);
            throw exception;
        }
        return List.copyOf(subscriptions);
    }
    private static CompletionStage<Void> invoke(Object target, Method method, Object event) {
        try {
            Object result = method.invoke(target, event);
            if (result instanceof CompletionStage<?> stage) return stage.thenApply(ignored -> null);
            return CompletableFuture.completedFuture(null);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            return CompletableFuture.failedFuture(cause);
        } catch (IllegalAccessException exception) { return CompletableFuture.failedFuture(exception); }
    }
    public EventDispatchResult publish(Object event) { return publish(event, EventCompletionPolicy.WAIT_FOR_ALL); }
    public EventDispatchResult publish(Object event, EventCompletionPolicy policy) {
        try { return publishAsync(event, policy).toCompletableFuture().join(); }
        catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            throw exception;
        }
    }
    public CompletionStage<EventDispatchResult> publishAsync(Object event) {
        return publishAsync(event, EventCompletionPolicy.WAIT_FOR_ALL);
    }
    public CompletionStage<EventDispatchResult> publishAsync(Object event, EventCompletionPolicy policy) {
        Objects.requireNonNull(event); Objects.requireNonNull(policy); eventsPublished.incrementAndGet();
        List<Handler> matched = new ArrayList<>();
        handlers.forEach((type, registered) -> { if (type.isAssignableFrom(event.getClass())) matched.addAll(registered); });
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (Handler handler : matched) pending.add(deliver(handler, event));
        String eventType = event.getClass().getName();
        if (policy == EventCompletionPolicy.FIRE_AND_FORGET)
            return CompletableFuture.completedFuture(new EventDispatchResult(eventType, matched.size(), 0, true));
        if (policy == EventCompletionPolicy.FAIL_FAST) {
            CompletableFuture<EventDispatchResult> result = new CompletableFuture<>();
            AtomicInteger finished = new AtomicInteger();
            for (CompletableFuture<Void> future : pending) future.whenComplete((ignored, error) -> {
                if (error != null) result.completeExceptionally(new EventDispatchException("Event listener failed", List.of(unwrap(error))));
                else if (finished.incrementAndGet() == pending.size())
                    result.complete(new EventDispatchResult(eventType, pending.size(), pending.size(), false));
            });
            if (pending.isEmpty()) result.complete(new EventDispatchResult(eventType, 0, 0, false));
            return result;
        }
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).handle((ignored, error) -> {
            List<Throwable> failures = new ArrayList<>();
            for (CompletableFuture<Void> future : pending) if (future.isCompletedExceptionally()) {
                try { future.join(); } catch (CompletionException exception) { failures.add(unwrap(exception)); }
            }
            if (!failures.isEmpty()) throw new CompletionException(new EventDispatchException("Event listener failure(s)", failures));
            return new EventDispatchResult(eventType, matched.size(), matched.size(), false);
        });
    }
    private CompletableFuture<Void> deliver(Handler handler, Object event) {
        UUID id = journal.begin(event.getClass().getName(), handler.id(), event.toString());
        CompletableFuture<Void> future;
        if (handler.delivery() == EventDelivery.ASYNC) {
            try {
                future = CompletableFuture.supplyAsync(() -> handler.invoke().apply(event), executor)
                    .thenCompose(stage -> stage).toCompletableFuture();
            } catch (RuntimeException exception) { future = CompletableFuture.failedFuture(exception); }
        } else {
            try { future = handler.invoke().apply(event).toCompletableFuture(); }
            catch (Throwable exception) { future = CompletableFuture.failedFuture(exception); }
        }
        return future.whenComplete((ignored, error) -> {
            if (error == null) { journal.complete(id); handlersCompleted.incrementAndGet(); }
            else { journal.fail(id, unwrap(error).toString()); handlersFailed.incrementAndGet(); }
        });
    }
    private static Throwable unwrap(Throwable throwable) {
        while (throwable instanceof CompletionException && throwable.getCause() != null) throwable = throwable.getCause();
        return throwable;
    }
    public long eventsPublished() { return eventsPublished.get(); }
    public long handlersCompleted() { return handlersCompleted.get(); }
    public long handlersFailed() { return handlersFailed.get(); }
    @FunctionalInterface public interface Subscription extends AutoCloseable { @Override void close(); }
    @FunctionalInterface private interface Invoker { CompletionStage<Void> apply(Object event); }
    private record Handler(String id, EventDelivery delivery, Invoker invoke) { }
}

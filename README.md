# JavaModulith

**A lightweight, platform-independent Java 21 modular application framework.** Inspired by modular-monolith architecture, with explicit module APIs, deterministic lifecycle management, a typed in-process event bus, optional SQLite or MongoDB publication tracking, architecture checks and testing utilities. No Bukkit, Paper, Spring, or Minecraft runtime dependencies.

> Current development: **0.2.0-SNAPSHOT** (unreleased; v0.1.0 remains the previous release) · Java 21 · MIT · Maintained by **el211 / Oreo Studios**

JavaModulith is a new, separate project adapted from the *platform-independent architectural ideas* behind [MinecraftModulith](https://github.com/el211/MinecraftModulith). It is **not** the official Spring Modulith project and does **not** require Spring Boot.

## Project layout

| Module | Purpose |
| --- | --- |
| `modulith-core` | Generic runtime, services, event bus, dependency graph and diagnostics |
| `modulith-processor` | Compile-time declaration validation (`@ApplicationModule`, `@ModuleApi`) |
| `modulith-events-sqlite` | Optional SQLite implementation of the `EventJournal` SPI |
| `modulith-events-mongodb` | Optional MongoDB synchronous-driver implementation of `EventJournal` |
| `modulith-libgdx` | Queue work safely onto the LibGDX render thread |
| `modulith-jme` | Queue work safely onto jMonkeyEngine's update/render thread |
| `modulith-test` | A focused module test harness and assertion helpers |
| `example-app` | Plain Java console application demonstrating the API |

## Quick start

The demo runs without Minecraft or an application server:

```bash
gradle :example-app:run
```

Create an API boundary:

```java
@ModuleApi("payments")
public interface BillingService {
    long balance(String customer);
}
```

Publish it from the owning module:

```java
@ApplicationModule("billing")
public final class BillingModule implements ModulithModule, BillingService {
    @Override
    public void start(ModuleContext context) {
        context.services().publish(BillingService.class, this);
    }

    @Override public long balance(String customer) { return 100; }
}
```

Consume only the named API:

```java
@ApplicationModule(value = "orders", dependencies = "billing::payments")
public final class OrdersModule implements ModulithModule {
    @Override
    public void start(ModuleContext context) {
        BillingService billing = context.services().require(BillingService.class);
        System.out.println(billing.balance("alice"));
    }
}
```

Bootstrap modules with explicit registration (no package scanning or hidden classpath magic):

```java
try (ModuleRuntime app = ModuleRuntime.builder()
        .module(OrdersModule.class)
        .module(BillingModule.class)
        .start()) {
    System.out.println(app.diagnostics());
    System.out.println(app.graphMermaid());
}
```

Modules start in dependency order and stop in reverse order. Module factories are supported with `module(MyModule.class, () -> new MyModule(...))`; use `externalService(MyInterface.class, instance)` to inject host-provided facilities and `context.external(MyInterface.class)` to access them.

## Events

A listener can be a method on an application module (discovered automatically after `start`) or a separate object registered with `context.listen(listener)`:

```java
@ModuleListener
public void onOrderPlaced(OrderPlaced event) {
    System.out.println("Order placed: " + event);
}
```

Use `delivery = EventDelivery.ASYNC` for asynchronous execution; listener methods may return `void` or `CompletionStage<?>`. An explicit executor can be provided via `builder.eventExecutor(executor)`. Events support `WAIT_FOR_ALL`, `FAIL_FAST` and `FIRE_AND_FORGET`; the last option returns before listeners complete.

```java
app.events().publish(new OrderPlaced("alice", 20));
app.events().publishAsync(new OrderPlaced("bob", 15));
```

Modules own the subscriptions and resources they register using `context.lifecycle()`, so registrations are released on shutdown and after failed starts. In-flight async handlers are **not** forcibly cancelled during shutdown; coordinate your executor appropriately.

## Optional persistent event journal

```java
EventJournal journal = new SqliteEventJournal(Path.of("data/events.db"));
try (var app = ModuleRuntime.builder()
        .eventJournal(journal)
        .module(BillingModule.class)
        .start()) {
    System.out.println(app.diagnostics().incompletePublications());
}
```

`modulith-events-sqlite` depends on the standard Java SQL API at compile time and requires the `sqlite-jdbc` dependency at runtime (declared as `runtimeOnly`). It records per-listener publication state (`PENDING`, `COMPLETED`, `FAILED`) and allows querying incomplete publications. **Automatic retry/replay is not implemented**; recovery remains the host application's responsibility. The default payload representation uses `Object.toString()` and is not intended as a reversible serializer.

## Dependency boundaries and validation

- A module declares full-module dependencies (`"billing"`) or named API dependencies (`"billing::payments"`).
- `ModuleRuntime` rejects duplicate module IDs, missing dependencies, cycles and undeclared cross-module service access.
- `modulith-processor` validates source annotations, duplicate IDs, invalid selectors and cycles among source modules compiled together. Runtime validates the complete assembled graph. The processor is *not* a whole-program static reference checker.
- `@ModuleApi` requires a public annotated interface for published service contracts.
- `graphMermaid()` and `graphGraphviz()` generate dependency visualization sources.

## Testing

The `modulith-test` module can start a target module and its transitive dependencies:

```java
try (var harness = ModuleTestHarness.builder()
        .modules(List.of(BillingModule.class, OrdersModule.class))
        .target("orders")
        .start()) {
    harness.assertRunning("billing")
           .assertRunning("orders")
           .assertStartupOrder("billing", "orders");
}
```

```bash
gradle clean build
```

## Optional game-engine adapters (0.2.0-SNAPSHOT)

Neither game engine is required by `modulith-core`. Choose one adapter only if your project uses that engine. The corresponding engine library is a **compile-only** dependency in the adapter; your application must provide its normal engine dependency at runtime.

### LibGDX: `modulith-libgdx`

Create the dispatcher from your application's initialized LibGDX lifecycle (`create()` or later). It calls `Application.postRunnable(...)`, which runs the work on the render thread before a later frame.

```java
LibGdxDispatcher dispatcher = LibGdxDispatcher.current();

// Can be called from a background thread. Never blocks its caller:
CompletableFuture<Void> pending = dispatcher.run(() -> {
    stage.addActor(actor); // actual LibGDX work executes on render thread
});

dispatcher.submit(() -> texture.getWidth())
    .thenAccept(width -> System.out.println("Texture width: " + width));

ModuleRuntime runtime = ModuleRuntime.builder()
    .module(GameplayModule.class)
    .externalService(LibGdxDispatcher.class, dispatcher)
    .start();
```

Retrieve the dispatcher inside any module via `context.external(LibGdxDispatcher.class)`. It also implements `Executor`, so opt into render-thread delivery of *asynchronous* Modulith event listeners with `builder.eventExecutor(dispatcher)`.

### jMonkeyEngine: `modulith-jme`

Queue scene graph and engine work through jME's `Application.enqueue(Runnable)`. The engine processes the queued work at the beginning of its update/render loop:

```java
JmeDispatcher dispatcher = new JmeDispatcher(mySimpleApplication);

dispatcher.run(() -> {
    rootNode.attachChild(mySpatial); // safely on jME's engine thread
});

CompletableFuture<Integer> count = dispatcher.submit(
    () -> rootNode.getChildren().size()
);

ModuleRuntime runtime = ModuleRuntime.builder()
    .module(GameplayModule.class)
    .externalService(JmeDispatcher.class, dispatcher)
    .start();
```

**Thread safety:** both dispatchers return `CompletableFuture` objects, propagate task exceptions, and always enqueue rather than attempting to execute tasks inline. Do not call `join()`/`get()` on a queued future *from that same engine thread*. Likewise, when using the dispatcher as `eventExecutor`, use `publishAsync(...)` rather than a blocking `publish(..., WAIT_FOR_ALL)` from the engine thread. Long-running I/O (including MongoDB) belongs on separate background workers. LibGDX HTML5/Android targets require separate Java 21 compatibility evaluation.

## MongoDB support (0.2.0-SNAPSHOT)

**Yes, MongoDB is now supported through an optional module.** The existing `ModuleRuntime.Builder.externalService(...)` also lets you supply any application-managed `MongoClient` directly to modules; this addition specifically adds durable event-publication tracking alongside SQLite.

```kotlin
dependencies {
    implementation(project(":modulith-events-mongodb"))
}
```

Or, once a new tagged JitPack release is available, replace that project dependency with the corresponding published artifact.

```java
// Create/close MongoClient in the HOST application, not inside each module.
MongoClient client = MongoClients.create(System.getenv("MONGODB_URI"));
MongoDatabase database = client.getDatabase("my_application");

MongoEventJournal journal = new MongoEventJournal(database);
// Optional: requires connection and collection-index permissions.
journal.ensureIndexes();

try (ModuleRuntime app = ModuleRuntime.builder()
        .eventJournal(journal)
        .module(BillingModule.class)
        .start()) {
    System.out.println(app.diagnostics().incompletePublications());
}

// On host shutdown, close the MongoClient too.
client.close();
```

The journal stores publication ID, event type, listener, string payload, `PENDING`/`COMPLETED`/`FAILED` status, timestamps and error text. Use `journal.incomplete()` to inspect unfinished/failed deliveries. It uses the official **synchronous MongoDB Java driver (5.13.0)**; configure acknowledged write concern if durability is required. It does not provide automatic replay, full event serialization, general MongoDB repositories or transparent transactions. Avoid synchronous database operations on rendering threads.

## GitHub and JitPack

Repository: [el211/JavaModulith](https://github.com/el211/JavaModulith)

The included `jitpack.yml` uses Java 21 and runs `gradle clean build publishToMavenLocal --no-daemon`. The existing `v0.1.0` tag, if present, is for the earlier core-only release. The new optional adapters are currently on `main` as `0.2.0-SNAPSHOT`; do not assume they're available from the older tag. Tag a verified release separately when CI passes.

For the previous `v0.1.0` release, example multi-module coordinates are:

```kotlin
repositories { mavenCentral(); maven("https://jitpack.io") }

dependencies {
    implementation("com.github.el211.JavaModulith:modulith-core:v0.1.0")
    annotationProcessor("com.github.el211.JavaModulith:modulith-processor:v0.1.0")
    // optional
    implementation("com.github.el211.JavaModulith:modulith-events-sqlite:v0.1.0")
    testImplementation("com.github.el211.JavaModulith:modulith-test:v0.1.0")
}
```

**The following tagged coordinates require a `v0.1.0` tag and a successful JitPack build; the new engine/MongoDB modules need a later release tag.** For JitPack, `gradle.properties` uses the repository-specific group `com.github.el211.JavaModulith`. Update it if you choose a different owner/repository. Non-JitPack local publications default to version `0.1.0`, while JitPack uses the Git tag version.

## License

MIT © 2026 Oreo Studios (el211). Contributions and issue reports welcome.

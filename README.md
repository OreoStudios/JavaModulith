# JavaModulith

**A lightweight, platform-independent Java 21 modular application framework.** Inspired by modular-monolith architecture, with explicit module APIs, deterministic lifecycle management, a typed in-process event bus, optional SQLite publication tracking, architecture checks and testing utilities. No Bukkit, Paper, Spring, or Minecraft runtime dependencies.

> Initial release: **0.1.0** · Java 21 · MIT · Maintained by **el211 / Oreo Studios**

JavaModulith is a new, separate project adapted from the *platform-independent architectural ideas* behind [MinecraftModulith](https://github.com/el211/MinecraftModulith). It is **not** the official Spring Modulith project and does **not** require Spring Boot.

## Project layout

| Module | Purpose |
| --- | --- |
| `modulith-core` | Generic runtime, services, event bus, dependency graph and diagnostics |
| `modulith-processor` | Compile-time declaration validation (`@ApplicationModule`, `@ModuleApi`) |
| `modulith-events-sqlite` | Optional SQLite implementation of the `EventJournal` SPI |
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

## Publishing to your own new GitHub repository

This ZIP deliberately contains no `.git` directory or Minecraft plugin metadata.

1. Create a GitHub repository (suggested name: `JavaModulith`).
2. Change GitHub URLs in `README.md` and `build.gradle.kts` if you choose a different name.
3. Push the files, then create the version tag `v0.1.0`.
4. The included `jitpack.yml` runs `gradle clean build publishToMavenLocal --no-daemon` on JDK 21.

Once JitPack successfully builds that tag, multi-module coordinates for a repo named `el211/JavaModulith` are:

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

**JitPack coordinates are examples until you create the repository/tag and its build succeeds.** For JitPack, `gradle.properties` uses the repository-specific group `com.github.el211.JavaModulith`. Update it if you choose a different owner/repository. Non-JitPack local publications default to version `0.1.0`, while JitPack uses the Git tag version.

## License

MIT © 2026 Oreo Studios (el211). Contributions and issue reports welcome.

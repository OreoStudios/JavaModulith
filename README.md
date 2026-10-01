# JavaModulith

**A lightweight, platform-independent Java 21 modular application framework.** Inspired by modular-monolith architecture, with Spring Modulith-style package-first modules, named APIs, deterministic lifecycle management, a typed in-process event bus, optional SQLite, MongoDB or multi-dialect JDBC publication tracking, architecture checks and testing utilities. No Bukkit, Paper, Spring, or Minecraft runtime dependencies.

> Current development: **0.2.0-SNAPSHOT** (unreleased; v0.1.0 remains the previous release) · Java 21 · MIT · Maintained by **el211 / Oreo Studios**

JavaModulith is a new, separate project adapted from the *platform-independent architectural ideas* behind [MinecraftModulith](https://github.com/el211/MinecraftModulith). It is **not** the official Spring Modulith project and does **not** require Spring Boot.

## Project layout

| Module | Purpose |
| --- | --- |
| `modulith-core` | Generic runtime, services, event bus, dependency graph and diagnostics |
| `modulith-processor` | Compile-time declaration validation (`@ApplicationModule`, `@ModuleApi`) |
| `modulith-events-sqlite` | Original SQLite `EventJournal` adapter, retained for compatibility |
| `modulith-events-jdbc` | One portable JDBC `EventJournal` for PostgreSQL, MariaDB, MySQL and SQLite |
| `modulith-events-mongodb` | Optional MongoDB synchronous-driver implementation of `EventJournal` |
| `modulith-libgdx` | Queue work safely onto the LibGDX render thread |
| `modulith-jme` | Queue work safely onto jMonkeyEngine's update/render thread |
| `modulith-test` | A focused module test harness and assertion helpers |
| `example-app` | Plain Java console application demonstrating the API |

## Package-first modules (Spring Modulith style)

**Each direct child package of your application base package is a module by convention.** You no longer need to annotate or manually register one class per module. Declare dependency restrictions in `package-info.java`, and expose named interfaces with `@NamedInterface` on an API subpackage's `package-info.java`.

```text
com.example.shop/
  ShopApplication.java            # base package + bootstrap
  billing/
    package-info.java             # optional @ApplicationModule
    BillingLifecycle.java         # optional lifecycle, not the module declaration
    payments/
      package-info.java           # @NamedInterface("payments")
      BillingService.java
    internal/
      BillingRepository.java      # implementation details
  orders/
    package-info.java             # @ApplicationModule(allowedDependencies = "billing::payments")
    OrdersLifecycle.java
  audit/                           # implicit module, no package-info needed
    AuditLifecycle.java
```

### 1. Declare a package's metadata

`com/example/shop/orders/package-info.java`:

```java
@ApplicationModule(allowedDependencies = {"billing::payments"})
package com.example.shop.orders;

import dev.oreo.javamodulith.core.ApplicationModule;
```

The annotation is optional for a default module with no explicit dependencies. The module ID defaults to its package's last segment, for example `orders`. Override it with `@ApplicationModule("my-module")` when needed.

### 2. Publish a named interface

`com/example/shop/billing/payments/package-info.java`:

```java
@NamedInterface("payments")
package com.example.shop.billing.payments;

import dev.oreo.javamodulith.core.NamedInterface;
```

`com/example/shop/billing/payments/BillingService.java`:

```java
package com.example.shop.billing.payments;

public interface BillingService {
    long balance(String customer);
}
```

An optional lifecycle class, residing in `billing`, can publish the service through `ModuleContext`:

```java
package com.example.shop.billing;

@ModuleEntrypoint
public final class BillingLifecycle implements ModulithModule, BillingService {
    @Override
    public void start(ModuleContext context) {
        context.services().publish(BillingService.class, this);
    }

    @Override public long balance(String customer) { return 100L; }
}
```

A module may contain **no** `ModulithModule` at all: it still participates in the package dependency graph and uses a no-op lifecycle. If there is precisely one concrete `ModulithModule`, it is selected automatically. For multiple candidates, explicitly mark one with `@ModuleEntrypoint`.

### 3. Bootstrap from the base package

```java
try (var runtime = ModuleRuntime.builder()
        .basePackage("com.example.shop")
        .start()) {
    System.out.println(runtime.graphMermaid());
    System.out.println(runtime.diagnostics());
}
```

Equivalent shortcut:

```java
var runtime = ModuleRuntime.scan("com.example.shop").start();
```

The scanner discovers direct child packages from compiled classes in ordinary directories or JARs. It starts lifecycle entrypoints in dependency order and stops them in reverse order. It does not require Spring, Bukkit or a DI container. Constructors can be supplied through explicit module factories for legacy class modules.

**Migration:** the old `@ApplicationModule` on a concrete class and `builder().module(MyModule.class)` are still supported for existing projects. New applications should use package declarations and `basePackage(...)`.

**Boundary scope in 0.2.0-SNAPSHOT:** dependency graph validation and named service access checks operate at runtime; annotation processing validates declarations and cycles in compiled source. This is not yet a whole-program replacement for Spring Modulith/ArchUnit's complete Java-reference verification.

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

Lifecycle entrypoints own the subscriptions and resources they register using `context.lifecycle()`, so registrations are released on shutdown and after failed starts. In-flight async handlers are **not** forcibly cancelled during shutdown; coordinate your executor appropriately.

## Optional persistent event journal

```java
EventJournal journal = new SqliteEventJournal(Path.of("data/events.db"));
try (var app = ModuleRuntime.builder()
        .eventJournal(journal)
        .basePackage("com.example.shop")
        .start()) {
    System.out.println(app.diagnostics().incompletePublications());
}
```

`modulith-events-sqlite` depends on the standard Java SQL API at compile time and requires the `sqlite-jdbc` dependency at runtime (declared as `runtimeOnly`). It records per-listener publication state (`PENDING`, `COMPLETED`, `FAILED`) and allows querying incomplete publications. **Automatic retry/replay is not implemented**; recovery remains the host application's responsibility. The default payload representation uses `Object.toString()` and is not intended as a reversible serializer.

## SQL database support: PostgreSQL, MariaDB, MySQL and SQLite (0.2.0-SNAPSHOT)

The optional `modulith-events-jdbc` module implements **one shared `EventJournal`** using the standard `javax.sql.DataSource` API. Select a `JdbcDialect` for schema creation; all publication operations use the same prepared-statement implementation.

| Dialect | JDBC URL prefix | Runtime driver |
| --- | --- | --- |
| `JdbcDialect.POSTGRESQL` | `jdbc:postgresql:` | `org.postgresql:postgresql` |
| `JdbcDialect.MARIADB` | `jdbc:mariadb:` | `org.mariadb.jdbc:mariadb-java-client` |
| `JdbcDialect.MYSQL` | `jdbc:mysql:` | `com.mysql:mysql-connector-j` |
| `JdbcDialect.SQLITE` | `jdbc:sqlite:` | `org.xerial:sqlite-jdbc` |

In this repository:

```kotlin
dependencies {
    implementation(project(":modulith-events-jdbc"))
    runtimeOnly("org.postgresql:postgresql:42.7.5") // Choose ONE matching driver.
    // SQLite: runtimeOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    // MariaDB: runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    // MySQL: runtimeOnly("com.mysql:mysql-connector-j:9.2.0")
}
```

After a future tagged release successfully builds through JitPack, the library artifact will be `com.github.el211.JavaModulith:modulith-events-jdbc:<release-tag>`. The module intentionally does **not** transitively bundle all four JDBC drivers.

```java
import dev.oreo.javamodulith.jdbc.DriverManagerDataSource;
import dev.oreo.javamodulith.jdbc.JdbcDialect;
import dev.oreo.javamodulith.jdbc.JdbcEventJournal;

// For production, a pooled DataSource (such as HikariCP) is also supported.
var source = new DriverManagerDataSource(
    "jdbc:postgresql://localhost:5432/my_app", "app_user", "password"
);
var journal = new JdbcEventJournal(source, JdbcDialect.POSTGRESQL);

try (var runtime = ModuleRuntime.builder()
        .eventJournal(journal)
        .module(BillingModule.class)
        .start()) {
    // Events published by the runtime are recorded per listener.
    System.out.println(runtime.diagnostics().incompletePublications());
    journal.incomplete().forEach(System.out::println);
}
```

Change only the JDBC URL, driver dependency and dialect for MariaDB, MySQL or SQLite. For SQLite, for example:

```java
var journal = new JdbcEventJournal(
    new DriverManagerDataSource("jdbc:sqlite:events.db"),
    JdbcDialect.SQLITE
);
```

The journal automatically creates `modulith_event_publications` and its status/time index if absent. You can pass a custom unqualified table name as the third constructor argument. Tables store IDs, event/listener identifiers, payload text, `PENDING`/`COMPLETED`/`FAILED` state, UTC epoch-millisecond timestamps and error text. You may query `incomplete()` or explicitly purge older successful deliveries with `deleteCompletedBefore(Instant)`. SQL identifiers are validated, and SQL values use bound parameters.

**Important:** this is synchronous, independent-connection publication tracking, *not* a transaction-bound application outbox. The supplied `DataSource` must return auto-commit connections. The library does not automatically retry/replay failed events, serialize objects reversibly, or join caller-owned transactions. Run database I/O off LibGDX/jME render threads. The original `modulith-events-sqlite` module remains available; existing SQLite journal data is not silently migrated to the new JDBC table.

The core JDBC functionality is covered by SQLite tests on every build, plus optional live PostgreSQL, MariaDB and MySQL integration tests in GitHub Actions.

## Dependency boundaries and validation

- Modules are identified by **direct child packages** of the configured base package. Package-level `@ApplicationModule` supports `allowedDependencies = {"billing::payments"}`; `dependencies` remains a legacy alias.
- Named APIs are declared with `@NamedInterface("payments")` on an API subpackage's `package-info.java` (or on an interface). A public interface in a module's root package is an unnamed default API.
- Internal subpackages cannot publish contracts unless explicitly exposed as named APIs (or marked with the backward-compatible `@ModuleApi`).
- The runtime rejects unknown dependencies, duplicate module IDs, circular dependencies and undeclared cross-module service access.
- The annotation processor validates package/type declarations, duplicate explicit IDs, selector syntax, named interfaces and cycles among annotated source declarations. A full static inspection of all class/method bytecode references is not yet implemented.
- `graphMermaid()` and `graphGraphviz()` export the discovered package dependency graph.

## Testing

The `modulith-test` module supports Spring-style targeted package testing: only the requested module and its transitive dependencies start, so unrelated packages are not initialized.

```java
try (var harness = ModuleTestHarness.builder()
        .basePackage("com.example.shop")
        .target("orders")
        .start()) {
    harness.assertRunning("billing")
           .assertRunning("orders")
           .assertStartupOrder("billing", "orders");
}
```

The legacy class-based `modules(List.of(...))` test harness remains available. You can also select a dependency closure directly with `ModuleRuntime.builder().basePackage("com.example.shop").targetModule("orders").start()`.

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
    .basePackage("com.example.game")
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

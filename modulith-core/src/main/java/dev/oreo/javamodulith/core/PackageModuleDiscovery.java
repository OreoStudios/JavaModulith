package dev.oreo.javamodulith.core;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Convention-based module discovery: each direct child package of basePackage
 * defines one module. package-info.java can supply its ID and dependencies.
 *
 * <p>Discovers compiled classes in conventional exploded classpath directories
 * and JAR classpath entries. This deliberately does not load Spring or another
 * dependency-injection container. Application components are owned by the host;
 * lifecycle integration is optional via ModulithModule.</p>
 */
public final class PackageModuleDiscovery {
    private PackageModuleDiscovery() { }

    public static List<DiscoveredModule> discover(String basePackage) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = PackageModuleDiscovery.class.getClassLoader();
        return discover(basePackage, loader);
    }

    public static List<DiscoveredModule> discover(String basePackage, ClassLoader loader) {
        Objects.requireNonNull(basePackage, "basePackage");
        Objects.requireNonNull(loader, "loader");
        if (!basePackage.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*")) {
            throw new IllegalArgumentException("Invalid base package: " + basePackage);
        }

        Set<String> classNames = scan(basePackage, loader);
        if (classNames.isEmpty()) {
            throw new ModulithException("No compiled classes found under base package: " + basePackage
                    + " (ensure it is on the runtime classpath)");
        }
        String prefix = basePackage + ".";
        Map<String, Set<String>> byDirectChild = new LinkedHashMap<>();
        for (String name : classNames) {
            if (!name.startsWith(prefix)) continue;
            String rest = name.substring(prefix.length());
            int separator = rest.indexOf('.');
            if (separator <= 0) continue; // base-package classes are application/bootstrap code, not modules
            String childName = rest.substring(0, separator);
            byDirectChild.computeIfAbsent(childName, ignored -> new TreeSet<>()).add(name);
        }

        List<DiscoveredModule> modules = new ArrayList<>();
        byDirectChild.keySet().stream().sorted().forEach(child -> {
            String packageName = prefix + child;
            Package pkg = loadPackage(packageName, loader);
            ApplicationModule metadata = pkg == null ? null : pkg.getAnnotation(ApplicationModule.class);
            String id = metadata != null && !metadata.value().isBlank() ? metadata.value() : child;
            List<String> dependencies = metadata == null ? List.of() : List.of(metadata.dependencies());

            List<Class<? extends ModulithModule>> lifecycleTypes = new ArrayList<>();
            for (String binaryName : byDirectChild.get(child)) {
                if (binaryName.endsWith(".package-info") || binaryName.endsWith(".module-info")) continue;
                Class<?> type = load(binaryName, loader);
                if (ModulithModule.class.isAssignableFrom(type)
                        && !type.isInterface()
                        && !Modifier.isAbstract(type.getModifiers())
                        && !type.isAnonymousClass()
                        && !type.isLocalClass()) {
                    if (type.isAnnotationPresent(ApplicationModule.class)) {
                        throw new ModulithException("Package-discovered module '" + id
                                + "' contains type-level @ApplicationModule on " + binaryName
                                + ". Declare the module on " + packageName + "/package-info.java instead");
                    }
                    lifecycleTypes.add(type.asSubclass(ModulithModule.class));
                }
                if (type.isAnnotationPresent(ModuleEntrypoint.class)
                        && !ModulithModule.class.isAssignableFrom(type)) {
                    throw new ModulithException("@ModuleEntrypoint must implement ModulithModule: " + binaryName);
                }
            }

            List<Class<? extends ModulithModule>> marked = lifecycleTypes.stream()
                    .filter(type -> type.isAnnotationPresent(ModuleEntrypoint.class)).toList();
            if (marked.size() > 1) {
                throw new ModulithException("Multiple @ModuleEntrypoint classes in module '" + id + "': " + marked);
            }
            if (marked.isEmpty() && lifecycleTypes.size() > 1) {
                throw new ModulithException("Multiple ModulithModule implementations in '" + id
                        + "'; mark one with @ModuleEntrypoint: " + lifecycleTypes);
            }

            Class<? extends ModulithModule> implementation =
                    !marked.isEmpty() ? marked.getFirst() :
                    lifecycleTypes.isEmpty() ? EmptyModule.class : lifecycleTypes.getFirst();

            Supplier<? extends ModulithModule> supplier = implementation == EmptyModule.class
                    ? EmptyModule::new : () -> ModuleRuntime.Builder.create(implementation);

            modules.add(new DiscoveredModule(
                    new ModuleDescriptor(id, dependencies, implementation),
                    packageName, supplier));
        });
        return List.copyOf(modules);
    }

    public record DiscoveredModule(
            ModuleDescriptor descriptor, String packageName,
            Supplier<? extends ModulithModule> factory
    ) { }

    /** Allows a package containing only services/entities to be a module too. */
    public static final class EmptyModule implements ModulithModule { }

    private static Package loadPackage(String name, ClassLoader loader) {
        try {
            Class<?> info = Class.forName(name + ".package-info", false, loader);
            return info.getPackage();
        } catch (ClassNotFoundException ignored) {
            return loader.getDefinedPackage(name);
        } catch (LinkageError failure) {
            throw new ModulithException("Cannot read package-info for " + name, failure);
        }
    }

    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError failure) {
            throw new ModulithException("Cannot load discovered type " + name, failure);
        }
    }

    private static Set<String> scan(String basePackage, ClassLoader loader) {
        String root = basePackage.replace('.', '/');
        Set<String> names = new TreeSet<>();
        try {
            Enumeration<URL> resources = loader.getResources(root);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    scanDirectory(Path.of(url.toURI()), basePackage, names);
                } else if ("jar".equals(url.getProtocol())) {
                    JarURLConnection conn = (JarURLConnection) url.openConnection();
                    try (JarFile jar = new JarFile(Path.of(conn.getJarFileURL().toURI()).toFile())) {
                        scanJar(jar, root, names);
                    }
                }
            }
            // Some shaded JARs contain class entries without directory entries,
            // so ClassLoader.getResources(basePackage/path) may be empty.
            if (names.isEmpty() && loader instanceof java.net.URLClassLoader urlLoader) {
                for (URL url : urlLoader.getURLs()) {
                    if (!"file".equals(url.getProtocol())) continue;
                    Path path = Path.of(url.toURI());
                    if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                        try (JarFile jar = new JarFile(path.toFile())) { scanJar(jar, root, names); }
                    } else if (Files.isDirectory(path)) {
                        Path location = path.resolve(root);
                        if (Files.isDirectory(location)) scanDirectory(location, basePackage, names);
                    }
                }
            }
        } catch (Exception failure) {
            throw new ModulithException("Cannot discover modules under " + basePackage, failure);
        }
        return names;
    }

    private static void scanDirectory(Path directory, String basePackage, Set<String> names) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String relative = directory.relativize(path).toString().replace('\\', '/');
                if (!relative.endsWith(".class") || relative.contains("$")) continue;
                String binary = relative.substring(0, relative.length() - 6).replace('/', '.');
                names.add(basePackage + "." + binary);
            }
        }
    }

    private static void scanJar(JarFile jar, String root, Set<String> names) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith(root + "/") || !name.endsWith(".class") || name.contains("$")) continue;
            names.add(name.substring(0, name.length() - 6).replace('/', '.'));
        }
    }
}

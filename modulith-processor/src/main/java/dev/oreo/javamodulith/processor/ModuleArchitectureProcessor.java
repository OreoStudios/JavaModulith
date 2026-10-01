package dev.oreo.javamodulith.processor;

import dev.oreo.javamodulith.core.ApplicationModule;
import dev.oreo.javamodulith.core.ModuleApi;
import dev.oreo.javamodulith.core.ModuleEntrypoint;
import dev.oreo.javamodulith.core.ModulithModule;
import dev.oreo.javamodulith.core.NamedInterface;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Validates package-info.java based modules, optional named interfaces and legacy
 * type-level modules. Full assembled dependency graph is validated at runtime.
 *
 * <p>Module packages without @ApplicationModule are discovered by convention,
 * and do not require any annotation processor marker.</p>
 */
@SupportedAnnotationTypes({
        "dev.oreo.javamodulith.core.ApplicationModule",
        "dev.oreo.javamodulith.core.ModuleApi",
        "dev.oreo.javamodulith.core.NamedInterface",
        "dev.oreo.javamodulith.core.ModuleEntrypoint"
})
public final class ModuleArchitectureProcessor extends AbstractProcessor {
    private final Map<String, Entry> modules = new LinkedHashMap<>();
    private final Set<String> seen = new HashSet<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_21;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (Element element : round.getElementsAnnotatedWith(ModuleApi.class)) {
            if (element.getKind() != ElementKind.INTERFACE || !element.getModifiers().contains(Modifier.PUBLIC)) {
                error(element, "@ModuleApi must annotate a public interface");
            }
            if (element.getAnnotation(ModuleApi.class).value().isBlank()) {
                error(element, "@ModuleApi name cannot be blank");
            }
        }

        for (Element element : round.getElementsAnnotatedWith(NamedInterface.class)) {
            if (element.getKind() != ElementKind.PACKAGE
                    && (element.getKind() != ElementKind.INTERFACE
                    || !element.getModifiers().contains(Modifier.PUBLIC))) {
                error(element, "@NamedInterface must annotate a package or public interface");
            }
            if (element.getAnnotation(NamedInterface.class).value().isBlank()) {
                error(element, "@NamedInterface name cannot be blank");
            }
        }

        TypeElement contract = processingEnv.getElementUtils()
                .getTypeElement(ModulithModule.class.getCanonicalName());
        TypeMirror contractType = contract == null ? null : contract.asType();

        for (Element element : round.getElementsAnnotatedWith(ModuleEntrypoint.class)) {
            if (element.getKind() != ElementKind.CLASS
                    || element.getModifiers().contains(Modifier.ABSTRACT)) {
                error(element, "@ModuleEntrypoint requires a concrete class");
            } else if (contractType != null && !processingEnv.getTypeUtils()
                    .isAssignable(element.asType(), contractType)) {
                error(element, "@ModuleEntrypoint must implement ModulithModule");
            }
        }

        for (Element element : round.getElementsAnnotatedWith(ApplicationModule.class)) {
            String qualified;
            String conventionalId;

            if (element.getKind() == ElementKind.PACKAGE) {
                qualified = ((PackageElement) element).getQualifiedName().toString();
                conventionalId = qualified.substring(qualified.lastIndexOf('.') + 1);
            } else if (element.getKind() == ElementKind.CLASS
                    && !element.getModifiers().contains(Modifier.ABSTRACT)) {
                TypeElement type = (TypeElement) element;
                qualified = type.getQualifiedName().toString();
                conventionalId = type.getSimpleName().toString();
                if (contractType != null && !processingEnv.getTypeUtils()
                        .isAssignable(type.asType(), contractType)) {
                    error(element, "Type-level @ApplicationModule must implement ModulithModule");
                }
            } else {
                error(element, "@ApplicationModule must annotate a package or concrete ModulithModule class");
                continue;
            }

            String declarationKey = element.getKind() + ":" + qualified;
            if (!seen.add(declarationKey)) continue;

            ApplicationModule annotation = element.getAnnotation(ApplicationModule.class);
            String id = annotation.value().isBlank() ? conventionalId : annotation.value();
            if (!id.matches("[a-zA-Z][a-zA-Z0-9_.-]*"))
                error(element, "Invalid module ID: " + id);

            Entry existing = modules.putIfAbsent(id, new Entry(id, List.of(annotation.dependencies()), element));
            if (existing != null) error(element, "Duplicate explicit module ID: " + id);

            for (String dependency : annotation.dependencies()) {
                if (!dependency.matches("[a-zA-Z][a-zA-Z0-9_.-]*(::[a-zA-Z][a-zA-Z0-9_.-]*)?"))
                    error(element, "Invalid dependency selector: " + dependency);
                String owner = dependency.split("::", 2)[0];
                if (owner.equals(id)) error(element, "Module cannot depend on itself: " + id);
            }
        }

        if (round.processingOver()) {
            Set<String> complete = new HashSet<>();
            Set<String> visiting = new HashSet<>();
            for (Entry entry : modules.values()) visit(entry, visiting, complete);
        }
        return false;
    }

    private void visit(Entry entry, Set<String> visiting, Set<String> complete) {
        if (complete.contains(entry.id())) return;
        if (!visiting.add(entry.id())) {
            error(entry.element(), "Circular module dependency involving " + entry.id());
            return;
        }
        for (String dependency : entry.dependencies()) {
            Entry other = modules.get(dependency.split("::", 2)[0]);
            if (other == null || complete.contains(other.id())) continue;
            if (visiting.contains(other.id()))
                error(entry.element(), "Circular dependency: " + entry.id() + " -> " + other.id());
            else visit(other, visiting, complete);
        }
        visiting.remove(entry.id());
        complete.add(entry.id());
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private record Entry(String id, List<String> dependencies, Element element) { }
}

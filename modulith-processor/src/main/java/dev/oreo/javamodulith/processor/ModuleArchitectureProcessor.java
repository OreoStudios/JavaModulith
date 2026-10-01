package dev.oreo.javamodulith.processor;
import dev.oreo.javamodulith.core.ApplicationModule;
import dev.oreo.javamodulith.core.ModuleApi;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
/** Validates source-level module declarations; runtime performs full graph checks. */
@SupportedAnnotationTypes({"dev.oreo.javamodulith.core.ApplicationModule", "dev.oreo.javamodulith.core.ModuleApi"})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class ModuleArchitectureProcessor extends AbstractProcessor {
    private final Map<String, Entry> modules = new LinkedHashMap<>();
    private final Set<String> seen = new HashSet<>();
    @Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (Element element : round.getElementsAnnotatedWith(ModuleApi.class)) {
            if (element.getKind() != ElementKind.INTERFACE || !element.getModifiers().contains(Modifier.PUBLIC))
                error(element, "@ModuleApi must annotate a public interface");
            ModuleApi api=element.getAnnotation(ModuleApi.class);
            if (api.value().isBlank()) error(element, "@ModuleApi name cannot be blank");
        }
        for (Element element : round.getElementsAnnotatedWith(ApplicationModule.class)) {
            String qualified=((TypeElement)element).getQualifiedName().toString();
            if (!seen.add(qualified)) continue;
            if (element.getKind() != ElementKind.CLASS || element.getModifiers().contains(Modifier.ABSTRACT))
                error(element, "@ApplicationModule requires a concrete class");
            ApplicationModule annotation=element.getAnnotation(ApplicationModule.class);
            String id=annotation.value();
            if (!id.matches("[a-zA-Z][a-zA-Z0-9_.-]*")) error(element, "Invalid module id: " + id);
            Entry existing=modules.putIfAbsent(id, new Entry(id, List.of(annotation.dependencies()), element));
            if (existing != null) error(element, "Duplicate module id: " + id);
            for (String dep : annotation.dependencies()) {
                if (!dep.matches("[a-zA-Z][a-zA-Z0-9_.-]*(::[a-zA-Z][a-zA-Z0-9_.-]*)?"))
                    error(element, "Invalid dependency selector: " + dep);
                String owner=dep.split("::", 2)[0];
                if (owner.equals(id)) error(element, "Module may not depend on itself: " + id);
            }
        }
        if (round.processingOver()) {
            // Only flag cycles among modules compiled in this invocation. References to modules
            // supplied by another artifact are checked when assembling ModuleRuntime.
            Set<String> complete=new HashSet<>();
            Set<String> visiting=new HashSet<>();
            for (Entry entry : modules.values()) visit(entry,visiting,complete);
        }
        return false;
    }
    private void visit(Entry entry,Set<String> visiting,Set<String> complete) {
        if (complete.contains(entry.id())) return;
        if (!visiting.add(entry.id())) { error(entry.element(), "Circular module dependency involving " + entry.id()); return; }
        for (String dep : entry.deps()) {
            Entry next=modules.get(dep.split("::",2)[0]);
            if (next != null && !complete.contains(next.id()) && !visiting.contains(next.id())) visit(next,visiting,complete);
            else if (next != null && visiting.contains(next.id())) error(entry.element(), "Circular dependency to " + next.id());
        }
        visiting.remove(entry.id()); complete.add(entry.id());
    }
    private void error(Element element,String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,message,element);
    }
    private record Entry(String id,List<String> deps,Element element) { }
}

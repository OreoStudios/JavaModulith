package dev.oreo.javamodulith.core;
import java.util.*;
/** Immutable snapshot of a running application. */
public record RuntimeDiagnostics(Map<String, ModuleState> states, List<String> startupOrder,
                                 int serviceCount, long eventsPublished, long handlersCompleted,
                                 long handlersFailed, long incompletePublications) {
    public RuntimeDiagnostics {
        states = Map.copyOf(states);
        startupOrder = List.copyOf(startupOrder);
    }
}

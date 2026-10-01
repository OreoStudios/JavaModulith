package dev.oreo.javamodulith.core;
public record EventDispatchResult(String eventType, int listenerCount, int completedCount, boolean pending) { }

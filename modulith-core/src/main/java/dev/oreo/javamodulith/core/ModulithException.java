package dev.oreo.javamodulith.core;
public class ModulithException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public ModulithException(String message) { super(message); }
    public ModulithException(String message, Throwable cause) { super(message, cause); }
}

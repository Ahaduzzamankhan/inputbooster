package dev.inputbooster.integration.fakes;

/**
 * Stands in for a public API interface such as Sodium's
 * {@code ModOptionsBuilder}: a public type that declares a hook as a
 * {@code public abstract} member.
 */
public interface BuilderApiLike {

    /** The hook, declared public on a public type — reachable from anywhere. */
    String addPage(String page);
}
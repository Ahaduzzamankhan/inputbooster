package dev.inputbooster.integration.fakes;

/**
 * Stands in for Sodium's {@code ConfigEntryPoint} interface, so the bridge's
 * {@link java.lang.reflect.InvocationHandler} can be exercised in a test
 * without Sodium on the class path.
 */
public interface EntryPointLike {

    void registerConfigEarly(Object configBuilder);

    void registerConfigLate(Object configBuilder);

    /** A hook from a hypothetical future Sodium that the bridge must ignore. */
    void registerConfigSomethingNew(Object configBuilder);
}
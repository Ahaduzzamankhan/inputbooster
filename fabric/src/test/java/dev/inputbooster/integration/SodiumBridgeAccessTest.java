package dev.inputbooster.integration;

import dev.inputbooster.integration.fakes.BuilderApiLike;
import dev.inputbooster.integration.fakes.EntryPointLike;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression tests for the crash reported on 26.3:
 *
 * <pre>
 * Description: Mod 'inputbooster' failed while registering config options.
 * java.lang.reflect.UndeclaredThrowableException
 *   at SodiumOptionsBridge.addPage(SodiumOptionsBridge.java:168)
 * Caused by: java.lang.IllegalAccessException: class ...SodiumOptionsBridge cannot
 *   access a member of class ...ModOptionsBuilderImpl with modifiers "public"
 * </pre>
 *
 * <p>Sodium hands back a {@code ModOptionsBuilderImpl}, which is package-private.
 * Reading {@code addPage} off the runtime class produced a {@code Method} whose
 * modifiers are {@code public} and which {@link Method#invoke} still refused to
 * call, because the declaring class is unreachable from another package.
 *
 * <p>Both halves of the fix are pinned here: the lookup rule, and the rule that
 * the entry point may never throw — Sodium calls it inside a handler that ends
 * the game with {@code crashWithMessage}, so an escaping exception becomes a
 * crash report instead of a missing sidebar entry.
 */
class SodiumBridgeAccessTest {

    private static final String IMPL =
        "dev.inputbooster.integration.fakes.PackagePrivateImpl";

    /** Builds the package-private implementation without naming its type. */
    private static Object newImplementation() throws Exception {
        Class<?> impl = Class.forName(IMPL);
        var constructor = impl.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @Test
    void readingTheHookOffTheRuntimeClassIsRefused() throws Exception {
        // The shipped bug, reproduced exactly: the method reports "public" and
        // is still not callable from outside its declaring package.
        Object implementation = newImplementation();
        Method viaRuntimeClass = implementation.getClass().getMethod("addPage", String.class);

        assertEquals("public", Modifier.toString(viaRuntimeClass.getModifiers()).trim(),
            "the method really is public; only the declaring class is not");

        assertThrows(IllegalAccessException.class,
            () -> viaRuntimeClass.invoke(implementation, "page"),
            "a hook read off a package-private implementation class cannot be invoked, "
                + "which is what crashed Minecraft");
    }

    @Test
    void readingTheHookOffThePublicApiTypeWorks() throws Exception {
        // The fix: the same member, read from the public interface it implements.
        Object implementation = newImplementation();
        Method viaApiType = BuilderApiLike.class.getMethod("addPage", String.class);

        assertEquals("added:page", viaApiType.invoke(implementation, "page"),
            "the public API type yields a callable method");
    }

    @Test
    void theEntryPointHandlerNeverThrows() throws Exception {
        // Deliberately typed as the concrete handler, whose invoke() declares no
        // checked exception: this call would not even compile otherwise.
        SodiumOptionsBridge.EntryPointHandler handler = new SodiumOptionsBridge.EntryPointHandler();
        Method late = EntryPointLike.class.getMethod("registerConfigLate", Object.class);
        Method early = EntryPointLike.class.getMethod("registerConfigEarly", Object.class);

        // Sodium is absent from the test class path, so building the page fails
        // here exactly as it would against a Sodium whose API had moved. That
        // failure must be absorbed: it used to escape as an
        // UndeclaredThrowableException, which Sodium reports with
        // crashWithMessage and which ends the game.
        assertDoesNotThrow(() -> handler.invoke(this, late, new Object[]{null}),
            "a failed page build must cost the sidebar entry, never the game");
        assertNull(handler.invoke(this, late, new Object[]{null}));
        assertNull(handler.invoke(this, early, new Object[]{null}));
    }

    @Test
    void theEntryPointHandlerImplementsTheObjectMethods() throws Exception {
        SodiumOptionsBridge.EntryPointHandler handler = new SodiumOptionsBridge.EntryPointHandler();
        Object proxy = new Object();

        assertEquals("InputBooster inputbooster Sodium config entry point",
            handler.invoke(proxy, Object.class.getMethod("toString"), null));
        assertEquals(System.identityHashCode(proxy),
            handler.invoke(proxy, Object.class.getMethod("hashCode"), null));
        Method equals = Object.class.getMethod("equals", Object.class);
        assertEquals(Boolean.TRUE, handler.invoke(proxy, equals, new Object[]{proxy}));
        assertEquals(Boolean.FALSE, handler.invoke(proxy, equals, new Object[]{new Object()}));
    }

    @Test
    void theEntryPointHandlerIgnoresHooksItDoesNotKnow() throws Exception {
        // A hook added by a future Sodium must be a no-op, not a crash.
        SodiumOptionsBridge.EntryPointHandler handler = new SodiumOptionsBridge.EntryPointHandler();
        Method futureHook =
            EntryPointLike.class.getMethod("registerConfigSomethingNew", Object.class);

        assertNull(handler.invoke(this, futureHook, new Object[]{null}),
            "an unrecognised hook returns null rather than failing");
    }

    @Test
    void theEntryPointHandlerIsAnInvocationHandler() {
        assertInstanceOf(InvocationHandler.class, new SodiumOptionsBridge.EntryPointHandler());
    }
}
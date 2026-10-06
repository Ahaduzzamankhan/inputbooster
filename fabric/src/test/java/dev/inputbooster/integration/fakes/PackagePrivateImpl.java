package dev.inputbooster.integration.fakes;

/**
 * Stands in for a package-private implementation class such as Sodium's
 * {@code ModOptionsBuilderImpl}: the class itself is package-private, while the
 * methods it implements are public.
 *
 * <p>This shape is the whole reason the Sodium bridge resolves methods on the
 * public API type. A {@code Method} read from this class reports
 * {@code public}, but {@code Method.invoke} still refuses to call it from
 * another package, because the <em>declaring</em> class is unreachable.
 */
class PackagePrivateImpl implements BuilderApiLike {

    @Override
    public String addPage(String page) {
        return "added:" + page;
    }
}
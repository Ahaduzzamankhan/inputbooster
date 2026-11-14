package dev.inputbooster;

/**
 * The colours the overlay text can be drawn in.
 *
 * <p>The configuration file stores the colour as a plain ARGB integer, so a
 * value that was written by hand is kept and still rendered. The user
 * interfaces offer these presets, because a colour picker cannot be built from
 * the widgets Sodium and vanilla share.
 */
public enum OverlayColor {

    WHITE("White", 0xFFE6EDF3),
    CYAN("Cyan", 0xFF55FFFF),
    GREEN("Green", 0xFF64D98B),
    YELLOW("Yellow", 0xFFFFB454),
    ORANGE("Orange", 0xFFFFAA00),
    RED("Red", 0xFFFF6B6B),
    MAGENTA("Magenta", 0xFFFF00FF),
    GREY("Grey", 0xFF9BA8B5);

    /** The name shown in the user interface. */
    public final String label;

    /** The ARGB value the preset writes into the configuration. */
    public final int argb;

    OverlayColor(String label, int argb) {
        this.label = label;
        this.argb = argb;
    }

    /**
     * The preset that matches a stored colour.
     *
     * <p>A colour that was written by hand has no preset, so the closest one is
     * offered instead. The stored value itself is never changed by the user
     * interface, only by an explicit choice.
     *
     * @param argb the stored colour
     * @return the matching preset, never {@code null}
     */
    public static OverlayColor closestTo(int argb) {
        for (OverlayColor color : values()) {
            if (color.argb == argb) return color;
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        OverlayColor best = WHITE;
        double bestDistance = Double.MAX_VALUE;
        for (OverlayColor color : values()) {
            int dr = r - ((color.argb >> 16) & 0xFF);
            int dg = g - ((color.argb >> 8) & 0xFF);
            int db = b - (color.argb & 0xFF);
            double distance = (double) (dr * dr + dg * dg + db * db);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = color;
            }
        }
        return best;
    }
}

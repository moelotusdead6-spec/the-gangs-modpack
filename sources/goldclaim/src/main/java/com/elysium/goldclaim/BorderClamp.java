package com.elysium.goldclaim;

public final class BorderClamp {
    public static final double MARGIN = 3.0;

    private BorderClamp() {
    }

    /** Nearest x/z at least {@code margin} blocks inside the border; positions already inside are unchanged. */
    public static double[] inside(double x, double z, double west, double east, double north, double south, double margin) {
        return new double[]{clamp(x, west, east, margin), clamp(z, north, south, margin)};
    }

    private static double clamp(double value, double min, double max, double margin) {
        double low = min + margin;
        double high = max - margin;
        if (low > high) {
            return (min + max) / 2.0;
        }
        return Math.max(low, Math.min(high, value));
    }
}

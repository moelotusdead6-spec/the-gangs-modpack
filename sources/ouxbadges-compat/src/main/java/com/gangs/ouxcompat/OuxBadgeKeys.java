package com.gangs.ouxcompat;

/** Oux Badges team names ({@code oux_badges_<key>}) and the glyphs its badge font maps them to. */
public final class OuxBadgeKeys {
    public static final String TEAM_PREFIX = "oux_badges_";
    static final String[] KEYS = {"1_owner", "2_mod", "3_gang", "4_drifter"};
    public static final int FIRST_GLYPH = 0xE000;
    /** Badge images taller than this are scaled down; the 256px font atlas cannot hold Oux's ~480px images. */
    public static final int MAX_GLYPH_HEIGHT = 64;

    private OuxBadgeKeys() {
    }

    /** Index into {@link #KEYS} for an Oux Badges team name, or -1. */
    public static int index(String teamName) {
        if (teamName == null || !teamName.startsWith(TEAM_PREFIX)) return -1;
        String key = teamName.substring(TEAM_PREFIX.length());
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return i;
        }
        return -1;
    }

    /** Tab order: Owner, Mod, Gang, Drifter, then players without a badge. */
    public static int priority(String teamName) {
        int index = index(teamName);
        return index < 0 ? KEYS.length : index;
    }

    public static String glyph(int index) {
        return new String(Character.toChars(FIRST_GLYPH + index));
    }

    public static boolean hasBadge(String text) {
        for (int i = 0; i < KEYS.length; i++) {
            if (text.indexOf(FIRST_GLYPH + i) >= 0) return true;
        }
        return false;
    }

    /** Target {width, height} for a badge image, or null if it already fits. */
    public static int[] shrunkSize(int width, int height) {
        if (height <= MAX_GLYPH_HEIGHT || width <= 0) return null;
        int newWidth = Math.max(1, (int)Math.round(width * (double)MAX_GLYPH_HEIGHT / height));
        return new int[]{newWidth, MAX_GLYPH_HEIGHT};
    }

    /** Area-averaged downscale of ABGR pixels with premultiplied alpha, so transparent edges do not darken. */
    public static int[] shrink(int[] pixels, int width, int height, int newWidth, int newHeight) {
        int[] result = new int[newWidth * newHeight];
        double sx = width / (double)newWidth;
        double sy = height / (double)newHeight;
        for (int y = 0; y < newHeight; y++) {
            double y0 = y * sy, y1 = y0 + sy;
            for (int x = 0; x < newWidth; x++) {
                double x0 = x * sx, x1 = x0 + sx;
                double area = 0, alpha = 0, c0 = 0, c1 = 0, c2 = 0;
                for (int py = (int)y0; py < Math.min(height, (int)Math.ceil(y1)); py++) {
                    double wy = Math.min(py + 1, y1) - Math.max(py, y0);
                    if (wy <= 0) continue;
                    for (int px = (int)x0; px < Math.min(width, (int)Math.ceil(x1)); px++) {
                        double wx = Math.min(px + 1, x1) - Math.max(px, x0);
                        if (wx <= 0) continue;
                        double weight = wx * wy;
                        int color = pixels[py * width + px];
                        double a = (color >>> 24) * weight;
                        area += weight;
                        alpha += a;
                        c0 += (color & 0xFF) * a;
                        c1 += (color >>> 8 & 0xFF) * a;
                        c2 += (color >>> 16 & 0xFF) * a;
                    }
                }
                int a = area == 0 ? 0 : (int)Math.round(alpha / area);
                int color = a << 24;
                if (alpha > 0) {
                    color |= channel(c2 / alpha) << 16 | channel(c1 / alpha) << 8 | channel(c0 / alpha);
                }
                result[y * newWidth + x] = color;
            }
        }
        return result;
    }

    private static int channel(double value) {
        return Math.max(0, Math.min(255, (int)Math.round(value)));
    }
}

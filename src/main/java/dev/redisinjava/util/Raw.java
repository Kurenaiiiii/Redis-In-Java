package dev.redisinjava.util;

import java.nio.charset.StandardCharsets;

/**
 * Byte/string conversions and number parsing shared by commands.
 *
 * <p>Keys and string values are carried as ISO-8859-1 decoded text: every
 * byte 0-255 maps to exactly one char and back, so binary payloads survive
 * untouched while the code can use plain {@link String} map keys.
 */
public final class Raw {
    private Raw() {
    }

    public static byte[] encode(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static String decode(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    /** Parses a Redis integer argument; null when not a valid long. */
    public static Long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /**
     * Parses a Redis float argument, accepting inf/-inf/+inf spellings.
     * Returns null for NaN and garbage.
     */
    public static Double parseDouble(String text) {
        String t = text.trim();
        if (t.equalsIgnoreCase("inf") || t.equalsIgnoreCase("+inf")) {
            return Double.POSITIVE_INFINITY;
        }
        if (t.equalsIgnoreCase("-inf")) {
            return Double.NEGATIVE_INFINITY;
        }
        try {
            double value = Double.parseDouble(t);
            return Double.isNaN(value) ? null : value;
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /**
     * Formats a score the way Redis prints it: integral values without a
     * decimal point, everything else via {@link Double#toString}.
     */
    public static String formatDouble(double value) {
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}

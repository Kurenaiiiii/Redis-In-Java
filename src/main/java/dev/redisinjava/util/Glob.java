package dev.redisinjava.util;

/**
 * Redis glob-style pattern matching for {@code KEYS} and {@code PSUBSCRIBE}.
 * Supports {@code *}, {@code ?}, {@code [abc]}, {@code [a-z]}, {@code [^ab]}
 * and backslash escapes.
 */
public final class Glob {
    private Glob() {
    }

    public static boolean match(String pattern, String text) {
        return match(pattern, 0, text, 0);
    }

    private static boolean match(String pattern, int pi, String text, int ti) {
        int pn = pattern.length();
        int tn = text.length();
        while (pi < pn) {
            char p = pattern.charAt(pi++);
            if (p == '*') {
                while (pi < pn && pattern.charAt(pi) == '*') {
                    pi++;
                }
                if (pi >= pn) {
                    return true;
                }
                for (int skip = ti; skip <= tn; skip++) {
                    if (match(pattern, pi, text, skip)) {
                        return true;
                    }
                }
                return false;
            }
            if (ti >= tn) {
                return false;
            }
            char t = text.charAt(ti++);
            if (p == '?') {
                continue;
            }
            if (p == '\\' && pi < pn) {
                if (pattern.charAt(pi++) != t) {
                    return false;
                }
                continue;
            }
            if (p == '[') {
                if (!matchClass(pattern, pi, t)) {
                    return false;
                }
                pi = skipClass(pattern, pi);
                if (pi < 0) {
                    return false;
                }
                continue;
            }
            if (p != t) {
                return false;
            }
        }
        return ti >= tn;
    }

    private static boolean matchClass(String pattern, int pi, char c) {
        int pn = pattern.length();
        boolean negate = pi < pn && pattern.charAt(pi) == '^';
        if (negate) {
            pi++;
        }
        boolean hit = false;
        boolean valid = false;
        while (pi < pn && pattern.charAt(pi) != ']') {
            valid = true;
            char lo = pattern.charAt(pi);
            if (lo == '\\' && pi + 1 < pn) {
                pi++;
                lo = pattern.charAt(pi);
            }
            if (pi + 2 < pn && pattern.charAt(pi + 1) == '-' && pattern.charAt(pi + 2) != ']') {
                char hi = pattern.charAt(pi + 2);
                if (lo <= c && c <= hi) {
                    hit = true;
                }
                pi += 3;
            } else {
                if (lo == c) {
                    hit = true;
                }
                pi++;
            }
        }
        if (!valid) {
            return false;
        }
        return negate != hit;
    }

    private static int skipClass(String pattern, int pi) {
        int pn = pattern.length();
        while (pi < pn && pattern.charAt(pi) != ']') {
            if (pattern.charAt(pi) == '\\' && pi + 1 < pn) {
                pi++;
            }
            pi++;
        }
        return pi < pn ? pi + 1 : -1;
    }
}

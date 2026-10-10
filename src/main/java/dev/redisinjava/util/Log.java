package dev.redisinjava.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimal leveled logger with zero dependencies. Level is set from
 * {@code --loglevel} or {@code CONFIG SET loglevel}; messages go to stdout
 * so container runtimes capture them.
 */
public final class Log {
    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    private static volatile Level level = Level.INFO;
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Log() {
    }

    public static void setLevel(Level next) {
        level = next;
    }

    public static Level getLevel() {
        return level;
    }

    public static Level parse(String text) {
        return switch (text.toLowerCase()) {
            case "debug" -> Level.DEBUG;
            case "info", "notice" -> Level.INFO;
            case "warning", "warn" -> Level.WARN;
            case "error" -> Level.ERROR;
            default -> null;
        };
    }

    public static void debug(String message) {
        log(Level.DEBUG, message);
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void warn(String message) {
        log(Level.WARN, message);
    }

    public static void error(String message) {
        log(Level.ERROR, message);
    }

    private static void log(Level at, String message) {
        if (at.ordinal() >= level.ordinal()) {
            System.out.println(LocalDateTime.now().format(FORMAT)
                    + " [" + at.name() + "] " + message);
        }
    }
}

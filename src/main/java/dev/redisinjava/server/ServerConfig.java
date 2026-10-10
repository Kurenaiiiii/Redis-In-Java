package dev.redisinjava.server;

import dev.redisinjava.util.Log;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All server settings. Defaults mirror real Redis; every field is readable
 * via {@code CONFIG GET} and the safe ones writable via {@code CONFIG SET}.
 */
public final class ServerConfig {
    private int port = 6379;
    private boolean appendOnly = false;
    private String appendFsync = "everysec";
    private String aofFile = "appendonly.aof";
    private long maxmemoryBytes = 0;
    private String maxmemoryPolicy = "noeviction";
    private int maxclients = 10000;
    private int timeoutSeconds = 0;
    private String loglevel = "info";
    private int autoRewritePercentage = 100;
    private long autoRewriteMinSize = 64 * 1024 * 1024;

    public int port() {
        return port;
    }

    /** Overrides the listen port; used by tests to bind an ephemeral port. */
    public void setPort(int port) {
        this.port = port;
    }

    public boolean appendOnly() {
        return appendOnly;
    }

    public String appendFsync() {
        return appendFsync;
    }

    public String aofFile() {
        return aofFile;
    }

    public long maxmemoryBytes() {
        return maxmemoryBytes;
    }

    public String maxmemoryPolicy() {
        return maxmemoryPolicy;
    }

    public int maxclients() {
        return maxclients;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    public String loglevel() {
        return loglevel;
    }

    public int autoRewritePercentage() {
        return autoRewritePercentage;
    }

    public long autoRewriteMinSize() {
        return autoRewriteMinSize;
    }

    /** Applies a CONFIG SET; returns an error message or null on success. */
    public synchronized String set(String name, String value) {
        switch (name.toLowerCase()) {
            case "maxmemory" -> {
                Long bytes = parseMemory(value);
                if (bytes == null || bytes < 0) {
                    return "ERR value is not an integer or out of range";
                }
                maxmemoryBytes = bytes;
            }
            case "maxmemory-policy" -> {
                if (!value.equals("noeviction") && !value.equals("allkeys-lru")) {
                    return "ERR Unsupported maxmemory-policy '" + value + "'";
                }
                maxmemoryPolicy = value;
            }
            case "appendonly" -> {
                if (value.equalsIgnoreCase("yes")) {
                    appendOnly = true;
                } else if (value.equalsIgnoreCase("no")) {
                    appendOnly = false;
                } else {
                    return "ERR Invalid argument '" + value + "' for CONFIG SET 'appendonly'";
                }
            }
            case "appendfsync" -> {
                if (!value.equals("always") && !value.equals("everysec") && !value.equals("no")) {
                    return "ERR Invalid argument '" + value + "' for CONFIG SET 'appendfsync'";
                }
                appendFsync = value;
            }
            case "auto-aof-rewrite-percentage" -> {
                try {
                    int pct = Integer.parseInt(value);
                    if (pct < 0) {
                        return "ERR value is not an integer or out of range";
                    }
                    autoRewritePercentage = pct;
                } catch (NumberFormatException bad) {
                    return "ERR value is not an integer or out of range";
                }
            }
            case "auto-aof-rewrite-min-size" -> {
                Long bytes = parseMemory(value);
                if (bytes == null || bytes < 0) {
                    return "ERR value is not an integer or out of range";
                }
                autoRewriteMinSize = bytes;
            }
            case "maxclients" -> {
                try {
                    int next = Integer.parseInt(value);
                    if (next <= 0) {
                        return "ERR value is not an integer or out of range";
                    }
                    maxclients = next;
                } catch (NumberFormatException bad) {
                    return "ERR value is not an integer or out of range";
                }
            }
            case "timeout" -> {
                try {
                    int next = Integer.parseInt(value);
                    if (next < 0) {
                        return "ERR value is not an integer or out of range";
                    }
                    timeoutSeconds = next;
                } catch (NumberFormatException bad) {
                    return "ERR value is not an integer or out of range";
                }
            }
            case "loglevel" -> {
                Log.Level parsed = Log.parse(value);
                if (parsed == null) {
                    return "ERR Invalid argument '" + value + "' for CONFIG SET 'loglevel'";
                }
                loglevel = value.toLowerCase();
                Log.setLevel(parsed);
            }
            default -> {
                return "ERR Unknown configuration parameter or out of range";
            }
        }
        return null;
    }

    /** All settings matching a glob pattern, for CONFIG GET. */
    public synchronized Map<String, String> get(String pattern) {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("port", Integer.toString(port));
        all.put("appendonly", appendOnly ? "yes" : "no");
        all.put("appendfsync", appendFsync);
        all.put("appendfilename", aofFile);
        all.put("maxmemory", Long.toString(maxmemoryBytes));
        all.put("maxmemory-policy", maxmemoryPolicy);
        all.put("maxclients", Integer.toString(maxclients));
        all.put("timeout", Integer.toString(timeoutSeconds));
        all.put("loglevel", loglevel);
        all.put("auto-aof-rewrite-percentage", Integer.toString(autoRewritePercentage));
        all.put("auto-aof-rewrite-min-size", Long.toString(autoRewriteMinSize));
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            if (dev.redisinjava.util.Glob.match(pattern.toLowerCase(), entry.getKey())) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    static Long parseMemory(String text) {
        String t = text.trim().toLowerCase();
        long mult = 1;
        if (t.endsWith("gb")) {
            mult = 1024 * 1024 * 1024;
            t = t.substring(0, t.length() - 2);
        } else if (t.endsWith("mb")) {
            mult = 1024 * 1024;
            t = t.substring(0, t.length() - 2);
        } else if (t.endsWith("kb")) {
            mult = 1024;
            t = t.substring(0, t.length() - 2);
        } else if (t.endsWith("b") && t.length() > 1) {
            t = t.substring(0, t.length() - 1);
        }
        try {
            return Long.parseLong(t.trim()) * mult;
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /** Parses CLI flags; returns null when the process should exit. */
    public static ServerConfig parseArgs(String[] args) {
        ServerConfig config = new ServerConfig();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> {
                    printHelp();
                    return null;
                }
                case "--version", "-v" -> {
                    System.out.println("Redis in Java 0.1.0");
                    return null;
                }
                case "--port" -> config.port = nextInt(args, ++i, "port");
                case "--appendonly" -> config.appendOnly = yesNo(args, ++i, "appendonly");
                case "--appendfsync" -> config.appendFsync = next(args, ++i, "appendfsync");
                case "--aof-file" -> config.aofFile = next(args, ++i, "aof-file");
                case "--maxmemory" -> {
                    Long bytes = parseMemory(next(args, ++i, "maxmemory"));
                    if (bytes == null) {
                        throw new IllegalArgumentException("Invalid --maxmemory value");
                    }
                    config.maxmemoryBytes = bytes;
                }
                case "--maxmemory-policy" -> config.maxmemoryPolicy = next(args, ++i, "maxmemory-policy");
                case "--maxclients" -> config.maxclients = nextInt(args, ++i, "maxclients");
                case "--timeout" -> config.timeoutSeconds = nextInt(args, ++i, "timeout");
                case "--loglevel" -> {
                    String level = next(args, ++i, "loglevel");
                    if (Log.parse(level) == null) {
                        throw new IllegalArgumentException("Invalid --loglevel value");
                    }
                    config.loglevel = level.toLowerCase();
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
            if (config.port < 0) {
                throw new IllegalArgumentException("Invalid option value");
            }
        }
        Log.setLevel(Log.parse(config.loglevel));
        return config;
    }

    private static String next(String[] args, int i, String option) {
        if (i >= args.length) {
            throw new IllegalArgumentException("Missing value for --" + option);
        }
        return args[i];
    }

    private static int nextInt(String[] args, int i, String option) {
        try {
            return Integer.parseInt(next(args, i, option));
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException("Invalid value for --" + option);
        }
    }

    private static boolean yesNo(String[] args, int i, String option) {
        String value = next(args, i, option);
        if (value.equalsIgnoreCase("yes")) {
            return true;
        }
        if (value.equalsIgnoreCase("no")) {
            return false;
        }
        throw new IllegalArgumentException("Invalid value for --" + option + " (yes/no)");
    }

    public static void printHelp() {
        System.out.println("""
                Redis in Java 0.1.0 - an independent educational Redis-compatible data store.
                Not affiliated with or endorsed by Redis Ltd.
                Usage: java -jar redis-in-java-0.1.0.jar [options]
                  --port <n>               TCP port (default 6379)
                  --appendonly <yes|no>     AOF persistence (default no)
                  --appendfsync <mode>      always|everysec|no (default everysec)
                  --aof-file <path>         AOF file (default appendonly.aof)
                  --maxmemory <bytes>       memory limit, supports kb/mb/gb (default 0 = unlimited)
                  --maxmemory-policy <p>    noeviction|allkeys-lru (default noeviction)
                  --maxclients <n>          connection limit (default 10000)
                  --timeout <seconds>       close idle connections (default 0 = off)
                  --loglevel <level>        debug|info|warn|error (default info)
                  --help                    this text
                  --version                 version""");
    }
}

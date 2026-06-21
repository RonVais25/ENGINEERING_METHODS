package server.db;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Tiny {@code .env} loader for the server's database credentials, so secrets such
 * as the MySQL password live in an untracked file rather than in source.
 *
 * <p>{@link #load()} reads the first {@code .env} it can find and copies each
 * {@code KEY=value} pair into the JVM's system properties (without overwriting
 * any property already set on the command line), while also keeping them in an
 * in-memory map readable via {@link #get(String)} and {@link #loadInto(BiConsumer)}.
 * The file is searched for in priority order: the path named by the
 * {@code gonature.env} system property, a {@code .env} next to the running JAR or
 * classes directory, a {@code .env} in the current working directory, and finally
 * {@code ~/.gonature.env}.
 *
 * <p>Loading is best-effort: a missing or unreadable file is treated as absent so
 * the server still starts. Lines are simple {@code KEY=value} pairs; blank lines
 * and {@code #} comments are skipped, and surrounding single or double quotes
 * around a value are stripped.
 */
public class DotEnv {

    /** Creates the .env loader (state and operations are static). */
    public DotEnv() { }

    /** Parsed key/value pairs from the {@code .env} file. */
    private static final Map<String, String> values = new HashMap<>();
    /** Whether {@link #load()} has already run (it is idempotent). */
    private static boolean loaded = false;

    /**
     * Locates and parses the {@code .env} file (see the class description for the
     * search order), copying every pair into the in-memory map and into system
     * properties that are not already set. Safe to call more than once: only the
     * first call does any work.
     */
    public static void load() {
        if (loaded) return;
        loaded = true;

        File file = locateEnvFile();
        if (file == null) return;

        Map<String, String> parsed = parse(file);
        values.putAll(parsed);

        for (Map.Entry<String, String> e : parsed.entrySet()) {
            if (System.getProperty(e.getKey()) == null) {
                System.setProperty(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * Returns the value loaded for a key from the {@code .env} file.
     *
     * @param key the key to look up
     * @return the value parsed from the file, or {@code null} if the key was not
     *         present (or {@link #load()} found no file)
     */
    public static String get(String key) {
        return values.get(key);
    }

    /**
     * Feeds every loaded key/value pair to the given consumer, letting callers
     * forward the {@code .env} contents elsewhere (for example into another
     * configuration object).
     *
     * @param sink receives each {@code (key, value)} pair currently loaded
     */
    public static void loadInto(BiConsumer<String, String> sink) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            sink.accept(e.getKey(), e.getValue());
        }
    }

    /** {@return the first {@code .env} file found in the search order, or {@code null}} */
    private static File locateEnvFile() {
        String override = System.getProperty("gonature.env");
        if (override != null && !override.isEmpty()) {
            File f = new File(override);
            if (f.isFile()) return f;
        }

        File nextToJar = jarSiblingEnv();
        if (nextToJar != null && nextToJar.isFile()) return nextToJar;

        File cwd = new File(".env");
        if (cwd.isFile()) return cwd;

        String home = System.getProperty("user.home");
        if (home != null) {
            File userLevel = new File(home, ".gonature.env");
            if (userLevel.isFile()) return userLevel;
        }

        return null;
    }

    /** {@return a {@code .env} beside the running JAR/classes dir, or {@code null}} */
    private static File jarSiblingEnv() {
        try {
            URL src = DotEnv.class.getProtectionDomain().getCodeSource().getLocation();
            if (src == null) return null;
            Path path = Paths.get(src.toURI());
            // path may be the JAR itself or a build/classes dir.
            File parent = path.toFile().getParentFile();
            if (parent == null) return null;
            return new File(parent, ".env");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses a {@code .env} file into key/value pairs (skips blanks and {@code #}
     * comments; strips surrounding quotes). Unreadable files yield an empty map.
     *
     * @param file the file to parse
     * @return the parsed key/value pairs
     */
    private static Map<String, String> parse(File file) {
        Map<String, String> out = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;

                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();

                if (value.length() >= 2) {
                    char first = value.charAt(0);
                    char last = value.charAt(value.length() - 1);
                    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                        value = value.substring(1, value.length() - 1);
                    }
                }

                if (!key.isEmpty()) out.put(key, value);
            }
        } catch (IOException ignored) {
            // Treat unreadable .env as absent — server should still start.
        }
        return out;
    }
}

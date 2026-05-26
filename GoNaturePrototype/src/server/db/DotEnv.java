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

public class DotEnv {

    private static final Map<String, String> values = new HashMap<>();
    private static boolean loaded = false;

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

    public static String get(String key) {
        return values.get(key);
    }

    public static void loadInto(BiConsumer<String, String> sink) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            sink.accept(e.getKey(), e.getValue());
        }
    }

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

package server.db;

public class DotEnv {
    public static String get(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}

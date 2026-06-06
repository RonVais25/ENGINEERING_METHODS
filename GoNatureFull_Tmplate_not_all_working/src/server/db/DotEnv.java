package server.db;

/**
 * Reads environment variables with a fallback default value.
 */
public class DotEnv {
    /**
     * Returns the value of an environment variable, or a default if it is unset or empty.
     *
     * @param key           the name of the environment variable to read
     * @param defaultValue  the value to return if the variable is unset or empty
     * @return              the environment variable's value, or defaultValue if unset or empty
     */
    public static String get(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
package server.util;

import java.util.function.Consumer;

public class ServerLog {
    private static Consumer<String> listener;
    public static void setListener(Consumer<String> consumer) { listener = consumer; }
    public static void log(String message) {
        System.out.println(message);
        if (listener != null) listener.accept(message);
    }
}

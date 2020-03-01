package ru.ifmo.java.server;

import java.util.*;

public class ServerFactory {
    public static final String THREAD_PER_CONNECTION_SERVER = "Thread per connection server";
    public static final String THREAD_POOL = "Thread pool server";
    public static final String NON_BLOCKING = "Non blocking server";
    public static final Set<String> SERVER_TYPES = new LinkedHashSet<>(Arrays.asList(
//            NEW_THREAD
            THREAD_PER_CONNECTION_SERVER, THREAD_POOL, NON_BLOCKING
    ));

    private ServerFactory() {
    }

    public static Server createServer(String name) {
        switch (name) {
            case THREAD_PER_CONNECTION_SERVER:
                return new ThreadPerConnectionServer();
            case THREAD_POOL:
                return new ThreadPoolServer(ServerMain.THREADS_NUM);
            case NON_BLOCKING:
                return new NonBlockingServer(ServerMain.THREADS_NUM);
            default:
                throw new IllegalArgumentException("Unknown server: " + name);
        }
    }
}

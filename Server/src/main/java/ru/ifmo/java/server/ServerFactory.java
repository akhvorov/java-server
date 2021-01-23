package ru.ifmo.java.server;

import java.io.IOException;

public class ServerFactory {
    private ServerFactory() {
    }

    public static Server createServer(String name) throws IOException {
        if (name.equals(Constants.ServerTypes.BLOCKING)) {
            return new BlockingServer(Constants.PORT, Constants.THREADS_NUM);
        } else if (name.equals(Constants.ServerTypes.NON_BLOCKING)) {
            return new NonBlockingServer(Constants.PORT, Constants.THREADS_NUM);
        } else if (name.equals(Constants.ServerTypes.ASYNCHRONOUS)) {
            return new AsyncServer(Constants.PORT, Constants.THREADS_NUM);
        }
        throw new IllegalArgumentException("Unknown server: " + name);
//        switch (name) {
//            case BLOCKING:
//                return new BlockingServer(Constants.PORT, Constants.THREADS_NUM);
//            case THREAD_PER_CONNECTION_SERVER:
//                return new ThreadPerConnectionServer();
//            case THREAD_POOL:
//                return new ThreadPoolServer(Constants.THREADS_NUM);
//            case NON_BLOCKING:
//                return new NonBlockingServer(Constants.THREADS_NUM);
//            default:
//                throw new IllegalArgumentException("Unknown server: " + name);
//        }
    }
}

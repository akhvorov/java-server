package ru.ifmo.java.server;

public class Constants {
    public static String HOST = "localhost";  // 192.168.0.49 (59)
    public static int STARTER_PORT = 4444;
    public static int PORT = 4445;
//    sudo lsof -i :4446
//    sudo kill -9 PID

    public static int THREADS_NUM = 8;

    static class ServerTypes {
        public static String BLOCKING = "blocking";
        public static String NON_BLOCKING = "non blocking";
        public static String ASYNCHRONOUS = "asynchronous";
    }

    static class Metric {
        public static String CLIENT = "Client";
        public static String RESPONSE = "Response";
        public static String HANDLE = "Handle";
    }
}

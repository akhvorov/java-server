package ru.ifmo.java.server;

public class Constants {
    public static String HOST = "localhost";
    public static int STARTER_PORT = 4444;
    public static int PORT = 4445;
//    sudo lsof -i :4446
//    sudo kill -9 PID

    public static int THREADS_NUM = 8;

//    public static String THREADS_NUM = 8;
    static class ServerTypes {
        public static String BLOCKING = "blocking";
    }
}

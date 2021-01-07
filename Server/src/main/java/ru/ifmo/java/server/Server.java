package ru.ifmo.java.server;

import java.io.*;
import java.util.Collections;
import java.util.List;

public abstract class Server implements Closeable {
//    protected Thread serverThread = new Thread(this);
    protected TimeMeasurer handleTimeMeasure = new TimeMeasurer();
    protected TimeMeasurer responseTimeMeasure = new TimeMeasurer();
    protected int port;

    public Server(int port) {
        this.port = port;
    }

    public abstract void run() throws IOException;

    protected void sort(List<Integer> list) {
//        TimeMeasurer.Timer timer = handleTimeMeasure.startNewTimer();
        System.out.println("before sort");
        System.out.println(list);
        list.sort(null);
        System.out.println("After sort");
        System.out.println(list);
//        timer.stop();
    }

    public double getHandleTime(double defaultScore) {
        return handleTimeMeasure.scoreOrDefault(defaultScore);
    }

    public double getResponseTime(double defaultScore) {
        return responseTimeMeasure.scoreOrDefault(defaultScore);
    }
}

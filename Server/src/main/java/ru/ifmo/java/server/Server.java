package ru.ifmo.java.server;

import java.io.*;
import java.util.List;

public abstract class Server implements Runnable, Closeable {
    protected Thread serverThread = new Thread(this);
    protected TimeMeasurer handleTimeMeasure = new TimeMeasurer();
    protected TimeMeasurer responseTimeMeasure = new TimeMeasurer();
    protected int port;

    public Server(int port) {
        this.port = port;
    }

    public void start() {
        serverThread.start();
    }

    protected void sort(List<Integer> list) {
        TimeMeasurer.Timer timer = handleTimeMeasure.startNewTimer();
        list.sort(null);
        timer.stop();
    }

    public double getHandleTime(double defaultScore) {
        return handleTimeMeasure.scoreOrDefault(defaultScore);
    }

    public double getResponseTime(double defaultScore) {
        return responseTimeMeasure.scoreOrDefault(defaultScore);
    }
}

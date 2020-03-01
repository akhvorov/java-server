package ru.ifmo.java.server;

import java.io.*;
import java.util.Collections;
import java.util.List;

public abstract class Server implements Closeable, Runnable {
    protected Thread serverThread = new Thread(this);
    protected TimeMeasurer handleTimeMeasure = new TimeMeasurer();
    protected TimeMeasurer responseTimeMeasure = new TimeMeasurer();
    protected int port;

    public void start(int port) throws IOException {
        this.port = port;
    }

    protected void sort(List<Integer> list) {
        TimeMeasurer.Timer timer = handleTimeMeasure.startNewTimer();
        Collections.sort(list);
        timer.stop();
    }

    public double getHandleTime(double defaultScore) {
        return handleTimeMeasure.scoreOrDefault(defaultScore);
    }

    public double getResponseTime(double defaultScore) {
        return responseTimeMeasure.scoreOrDefault(defaultScore);
    }
}

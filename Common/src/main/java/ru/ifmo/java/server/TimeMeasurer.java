package ru.ifmo.java.server;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TimeMeasurer {
    private static final double MILLIS = 1e6;
    private AtomicLong sum = new AtomicLong(0);
    private AtomicInteger count = new AtomicInteger(0);

    public Timer startNewTimer() {
        return new Timer();
    }

    public double score() {
        if (count.get() == 0) {
            throw new IllegalStateException("No values to score");
        }
        return (double) sum.get() / count.get() / MILLIS;
    }

    public double scoreOrDefault(double defaultScore) {
        if (count.get() == 0) {
            return defaultScore;
        }
        return (double) sum.get() / count.get() / MILLIS;
    }

    public class Timer {
        private long startTime = System.nanoTime();

        public void stop() {
            sum.addAndGet(System.nanoTime() - startTime);
            count.incrementAndGet();
        }
    }
}

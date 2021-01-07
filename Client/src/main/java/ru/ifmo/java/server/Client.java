package ru.ifmo.java.server;

import ru.ifmo.java.server.protocol.ArraySortRequest;
import ru.ifmo.java.server.protocol.ArraySortResponse;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;


public class Client implements Callable<Double> {
    private final String host;
    private final int port;

    private final int queriesNum;
    private final int listSize;
    private final int delay;

    private static final Random random = new Random();

    public Client(String host, int port, int queriesNum, int listSize, int delay) {
        this.host = host;
        this.port = port;
        this.queriesNum = queriesNum;
        this.listSize = listSize;
        this.delay = delay;
    }

    public Double call() throws IOException {
        TimeMeasurer timeMeasurer = new TimeMeasurer();
        TimeMeasurer.Timer timer = timeMeasurer.startNewTimer();
        try (Socket socket = new Socket(host, port)) {
            for (int i = 0; i < queriesNum; i++) {
                List<Integer> array = random.ints().limit(listSize).boxed().collect(Collectors.toList());
                ArraySortRequest.newBuilder()
                        .addAllValues(array)
                        .build()
                        .writeDelimitedTo(socket.getOutputStream());
                ArraySortResponse response = ArraySortResponse.parseDelimitedFrom(socket.getInputStream());
                assert response != null;
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        timer.stop();
        return timeMeasurer.score();
    }
}

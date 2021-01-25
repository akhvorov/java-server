package ru.ifmo.java.server;

import ru.ifmo.java.server.protocol.StarterRequest;
import ru.ifmo.java.server.protocol.StatResponse;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static ru.ifmo.java.server.Constants.Metric.*;

public class ClientsRunner {
    public static void main(String[] args) throws IOException {
        System.out.println("Start client");
        ClientsRunner runner = new ClientsRunner();
        Map<String, Double> metrics = runner.run(Constants.ServerTypes.BLOCKING, 7, 20, 50, 100, Constants.HOST);
//        Map<String, Double> metrics = runner.run(Constants.ServerTypes.NON_BLOCKING, 7, 20, 50, 100, Constants.HOST);
//        Map<String, Double> metrics = runner.run(Constants.ServerTypes.ASYNCHRONOUS, 7, 20, 50, 100, Constants.HOST);

        runner.run(Constants.ServerTypes.NON_BLOCKING, 7, 20, 50, 100, Constants.HOST);
//        runner.run(Constants.ServerTypes.ASYNCHRONOUS, 7, 20, 50, 100, Constants.HOST);
        System.out.println(metrics);
    }

    public Map<String, Double> run(String serverType, int clientsNum, int queriesNum, int listSize, int delay, String host) throws IOException {
        try (Socket socket = new Socket(host, Constants.STARTER_PORT)) {
            StarterRequest.newBuilder()
                    .setServerType(serverType)
                    .setStart(true)
                    .build()
                    .writeDelimitedTo(socket.getOutputStream());
            StatResponse.parseDelimitedFrom(socket.getInputStream());

            ExecutorService clients = Executors.newFixedThreadPool(clientsNum);
            List<Future<Double>> times = new ArrayList<>();
            for (int i = 0; i < clientsNum; i++) {
                times.add(clients.submit(new Client(host, Constants.PORT, queriesNum, listSize, delay)));
            }

            double fullTime = 0.0;
            for (Future<Double> future : times) {
                try {
                    fullTime += future.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            clients.shutdown();
            StarterRequest.newBuilder()
                    .setServerType(serverType)
                    .setStart(false)
                    .build()
                    .writeDelimitedTo(socket.getOutputStream());
            StatResponse statResponse = StatResponse.parseDelimitedFrom(socket.getInputStream());
            assert statResponse != null;
            Map<String, Double> metrics = new HashMap<>();
            metrics.put(HANDLE, statResponse.getHandleTime());
            metrics.put(RESPONSE, statResponse.getResponseTime());
            metrics.put(CLIENT, fullTime / times.size());
            System.out.println(metrics);
            return metrics;
        }
    }

    public Map<Integer, Map<String, Double>> runCompare(String serverType, int clientsNum, int queriesNum, int listSize, int delay,
                           String changeName, int from, int to, int step, String host) {
        Map<Integer, Map<String, Double>> result = new HashMap<>();
        for (int val = from; val <= to; val += step) {
            switch (changeName) {
                case "M":
                    clientsNum = val;
                    break;
                case "N":
                    listSize = val;
                    break;
                case "X":
                    queriesNum = val;
                    break;
                case "Delay":
                    delay = val;
                    break;
                default:
                    throw new IllegalArgumentException("Illegal change name: " + changeName);
            }

            try {
                result.put(val, run(serverType, clientsNum, queriesNum, listSize, delay, host));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}

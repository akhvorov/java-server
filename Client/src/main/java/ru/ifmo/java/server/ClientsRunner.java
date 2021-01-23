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

public class ClientsRunner {
//    private final String serverType;
//    private final int clientsNum;
//    private final int queriesNum;
//    private final int listSize;
//    private final int delay;
//    private final ExecutorService clients;

    public static void main(String[] args) throws IOException {
        System.out.println("Start client");
        ClientsRunner runner = new ClientsRunner();
//        Map<String, Double> metrics = runner.run(Constants.ServerTypes.BLOCKING, 2, 3, 5, 100);
//        Map<String, Double> metrics = runner.run(Constants.ServerTypes.NON_BLOCKING, 7, 20, 50, 100);
        Map<String, Double> metrics = runner.run(Constants.ServerTypes.ASYNCHRONOUS, 7, 20, 50, 100);
        System.out.println(metrics);
    }

//    enum TimeType {
//        HANDLE,
//        SERVER_RESPONSE,
//        CLIENT_RESPONSE
//    }
//
//    private List<Integer> valRange = new ArrayList<>();
//    private Map<TimeType, List<Double>> times = new HashMap<>();
//
//    public ClientsRunner() {
//        this.serverType = serverType;
//        this.clientsNum = clientsNum;
//        this.queriesNum = queriesNum;
//        this.listSize = listSize;
//        this.delay = delay;

//        clients = Executors.newFixedThreadPool(clientsNum);
//        for (TimeType timeType : TimeType.values()) {
//            times.put(timeType, new ArrayList<>());
//        }
//    }

    public Map<String, Double> run(String serverType, int clientsNum, int queriesNum, int listSize, int delay) throws IOException {
        try (Socket socket = new Socket(Constants.HOST, Constants.STARTER_PORT)) {
            StarterRequest.newBuilder()
                    .setServerType(serverType)
                    .setStart(true)
                    .build()
                    .writeDelimitedTo(socket.getOutputStream());
            StatResponse.parseDelimitedFrom(socket.getInputStream());

            ExecutorService clients = Executors.newFixedThreadPool(clientsNum);
            List<Future<Double>> times = new ArrayList<>();
            for (int i = 0; i < clientsNum; i++) {
                times.add(clients.submit(new Client(Constants.HOST, Constants.PORT, queriesNum, listSize, delay)));
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
            socket.close();
            assert statResponse != null;
            Map<String, Double> metrics = new HashMap<>();
            metrics.put("Handle", statResponse.getHandleTime());
            metrics.put("Response", statResponse.getResponseTime());
            metrics.put("Client", fullTime / times.size());
            return metrics;
        }
    }

    public void runCompare(String serverType, int clientsNum, int queriesNum, int listSize, int delay,
                           String changeName, int from, int to, int step) throws IOException {
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

            run(serverType, clientsNum, queriesNum, listSize, delay);
        }
    }

//    public void runTest(int n, int m, int x, int delay, String changeName, int from, int to,
//                        int step, String serverIp, String serverType) {
//        for (int val = from; val <= to; val += step) {
//            try (Socket socket = new Socket(serverIp, Constants.STARTER_PORT);
//                 DataInputStream inputStream = new DataInputStream(socket.getInputStream());
//                 DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
//                StarterRequest.newBuilder().setServerType(serverType).build().writeDelimitedTo(outputStream);  // .setRequestsCount(m * x)
//                TimeMeasurer timeMeasurer = new TimeMeasurer();
//                ExecutorService threadPool = Executors.newCachedThreadPool();
//                switch (changeName) {
//                    case "M":
//                        m = val;
//                        break;
//                    case "N":
//                        n = val;
//                        break;
//                    case "X":
//                        x = val;
//                        break;
//                    case "Delay":
//                        delay = val;
//                        break;
//                    default:
//                        throw new IllegalArgumentException("Illegal change name: " + changeName);
//                }
//
//                for (int i = 0; i < m; i++) {
//                    final int n1 = n, x1 = x, delay1 = delay;
//                    threadPool.submit(() -> new Client(timeMeasurer).process(serverIp, Constants.PORT, n1, x1, delay1));
//                }
//                threadPool.shutdown();
//                try {
//                    threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//
//                valRange.add(val);
//                times.get(TimeType.CLIENT_RESPONSE).add(timeMeasurer.score());
//
//                StarterRequest.newBuilder().setServerType(serverType).build().writeDelimitedTo(outputStream);
//                StatResponse response = StatResponse.parseDelimitedFrom(inputStream);
//                if (response == null) {
//                    times.get(TimeType.HANDLE).add(-1.);
//                    times.get(TimeType.SERVER_RESPONSE).add(-1.);
//                } else {
//                    times.get(TimeType.HANDLE).add(response.getHandleTime());
//                    times.get(TimeType.SERVER_RESPONSE).add(response.getResponseTime());
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    public List<Integer> getValRange() {
//        return valRange;
//    }
//
//    public Map<TimeType, List<Double>> getTimes() {
//        return times;
//    }
}

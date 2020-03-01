package ru.ifmo.java.server;

import ru.ifmo.java.server.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ClientsRunner {
    enum TimeType {
        HANDLE,
        SERVER_RESPONSE,
        CLIENT_RESPONSE
    }

    private List<Integer> valRange = new ArrayList<>();
    private Map<TimeType, List<Double>> times = new HashMap<>();

    public ClientsRunner() {
        for (TimeType timeType : TimeType.values()) {
            times.put(timeType, new ArrayList<>());
        }
    }

    public void runTest(int n, int m, int x, int delay, String changeName, int from, int to,
                        int step, String serverIp, String serverType) {
        for (int val = from; val <= to; val += step) {
            try (Socket socket = new Socket(serverIp, ServerMain.PORT);
                 DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                 DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
                Protocol.Config.newBuilder().setServerType(serverType).setRequestsCount(m * x).build().writeDelimitedTo(outputStream);
                TimeMeasurer timeMeasurer = new TimeMeasurer();
                ExecutorService threadPool = Executors.newCachedThreadPool();
                switch (changeName) {
                    case "M":
                        m = val;
                        break;
                    case "N":
                        n = val;
                        break;
                    case "X":
                        x = val;
                        break;
                    case "Delay":
                        delay = val;
                        break;
                    default:
                        throw new IllegalArgumentException("Illegal change name: " + changeName);
                }

                for (int i = 0; i < m; i++) {
                    final int n1 = n, x1 = x, delay1 = delay;
                    threadPool.submit(() -> new Client(timeMeasurer).process(serverIp, ServerMain.SERVER_PORT, n1, x1, delay1));
                }
                threadPool.shutdown();
                try {
                    threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                valRange.add(val);
                times.get(TimeType.CLIENT_RESPONSE).add(timeMeasurer.score());

                Protocol.Config.newBuilder().setServerType(serverType).setRequestsCount(0).build().writeDelimitedTo(outputStream);
                Protocol.StatResponse response = Protocol.StatResponse.parseDelimitedFrom(inputStream);
                if (response == null) {
                    times.get(TimeType.HANDLE).add(-1.);
                    times.get(TimeType.SERVER_RESPONSE).add(-1.);
                } else {
                    times.get(TimeType.HANDLE).add(response.getHandleTime());
                    times.get(TimeType.SERVER_RESPONSE).add(response.getResponseTime());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<Integer> getValRange() {
        return valRange;
    }

    public Map<TimeType, List<Double>> getTimes() {
        return times;
    }
}

package ru.ifmo.java.server;

import ru.ifmo.java.server.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;


public class Client {
    private static Random random = new Random();
    private TimeMeasurer timeMeasurer;

    public Client(TimeMeasurer timeMeasurer) {
        this.timeMeasurer = timeMeasurer;
    }

    private static List<Integer> readArray(DataInputStream inputStream) throws IOException {
        int size = inputStream.readInt();
        byte[] data = new byte[size];
        int processed = 0;
        while (processed < size) {
            processed += inputStream.read(data, processed, size - processed);
        }
        return Protocol.ArraySortResponse.parseFrom(data).getValuesList();
    }

    private static void sendArray(Protocol.ArraySortRequest array,
                                  DataOutputStream outputStream) throws IOException {
        byte[] data = array.toByteArray();
        outputStream.writeInt(data.length);
        outputStream.write(data);
        outputStream.flush();
    }

    public void process(String serverIp, int serverPort, int n, int x, int delay) {
        TimeMeasurer.Timer timer = timeMeasurer.startNewTimer();
        try (Socket socket = new Socket(serverIp, serverPort);
             DataInputStream inputStream = new DataInputStream(socket.getInputStream());
             DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
            for (int i = 0; i < x; i++) {
                doQuery(n, inputStream, outputStream);
                Thread.sleep(delay);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        timer.stop();
    }

    private void doQuery(int n, DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        List<Integer> array = random.ints().limit(n).boxed().collect(Collectors.toList());
        Protocol.ArraySortRequest request = Protocol.ArraySortRequest.newBuilder().setSize(n).addAllValues(array).build();
        sendArray(request, outputStream);
        List<Integer> list = readArray(inputStream);
        int listSize = list.size();
    }
}

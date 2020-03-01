package ru.ifmo.java.server;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ThreadPerConnectionServer extends BlockingServer {
    @Override
    public void start(int port) throws IOException {
        super.start(port);
        serverThread.start();
    }

    @Override
    public void close() throws IOException {
        super.close();
        serverThread.interrupt();
    }

    @Override
    public void run() {
        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> {
                    try (DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
                         OutputStream outputStream = clientSocket.getOutputStream()) {
                        while (!clientSocket.isClosed()) {
                            TimeMeasurer.Timer timer = responseTimeMeasure.startNewTimer();
                            List<Integer> list = readArray(inputStream);
                            sort(list);
                            writeArray(outputStream, list);
                            timer.stop();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

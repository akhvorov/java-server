package ru.ifmo.java.server;

import ru.ifmo.java.server.protocol.ArraySortRequest;
import ru.ifmo.java.server.protocol.ArraySortResponse;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockingServer extends Server {
    protected ServerSocket serverSocket;
    private final ExecutorService requestThreadPool = Executors.newCachedThreadPool();
    private final ExecutorService workersThreadPool;

    public BlockingServer(int port, int nThreads) throws IOException {
        super(port);
        serverSocket = new ServerSocket(port);
        workersThreadPool = Executors.newFixedThreadPool(nThreads);
    }

    public void run() {
        while (!serverSocket.isClosed()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
                requestThreadPool.submit(new ServerWorker(socket));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }

    class ServerWorker implements Runnable {
        private final Socket socket;
        private final ExecutorService responseThreadPool = Executors.newSingleThreadExecutor();

        public ServerWorker(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            while (!socket.isClosed()) {
                try {
                    TimeMeasurer.Timer timer = responseTimeMeasure.startNewTimer();
                    List<Integer> list = new ArrayList<>(ArraySortRequest.parseDelimitedFrom(socket.getInputStream()).getValuesList());

                    workersThreadPool.submit(() -> {
                        sort(list);
                        responseThreadPool.submit(() -> {
                            try {
                                ArraySortResponse.newBuilder()
                                        .addAllValues(list)
                                        .build()
                                        .writeDelimitedTo(socket.getOutputStream());
                                timer.stop();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

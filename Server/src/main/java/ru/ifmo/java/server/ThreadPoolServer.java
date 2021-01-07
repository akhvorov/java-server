package ru.ifmo.java.server;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//public class ThreadPoolServer extends BlockingServer {
//    private ExecutorService requestThreadPool = Executors.newCachedThreadPool();
//    private ExecutorService workersThreadPool;
//
//    public ThreadPoolServer(int threadNum) {
//        workersThreadPool = Executors.newFixedThreadPool(threadNum);
//    }
//
//    @Override
//    public void start(int port) throws IOException {
//        super.start(port);
//        serverThread.start();
//    }
//
//    @Override
//    public void close() throws IOException {
//        workersThreadPool.shutdown();
//        requestThreadPool.shutdown();
//        serverThread.interrupt();
//        super.close();
//    }
//
//    @Override
//    public void run() {
//        while (!serverSocket.isClosed()) {
//            try {
//                final Socket clientSocket = serverSocket.accept();
//                requestThreadPool.submit(new Worker(clientSocket));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    private class Worker implements Runnable {
//        private ExecutorService responseThreadPool = Executors.newSingleThreadExecutor();
//        private Socket clientSocket;
//
//        public Worker(Socket clientSocket) {
//            this.clientSocket = clientSocket;
//        }
//
//        @Override
//        public void run() {
//            try (DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
//                 OutputStream outputStream = clientSocket.getOutputStream()) {
//                while (!clientSocket.isClosed()) {
//                    TimeMeasurer.Timer timer = responseTimeMeasure.startNewTimer();
//                    List<Integer> list = readArray(inputStream);
//                    workersThreadPool.submit(() -> {
//                        sort(list);
//                        responseThreadPool.submit(() -> {
//                            try {
//                                writeArray(outputStream, list);
//                                timer.stop();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                        });
//                    });
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//}

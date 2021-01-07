package ru.ifmo.java.server;

import ru.ifmo.java.server.protocol.StarterRequest;
import ru.ifmo.java.server.protocol.StatResponse;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ServerMain {
    public static void main(String[] args) throws IOException {
        System.out.println("Server started");
        try (ServerSocket serverSocket = new ServerSocket(Constants.STARTER_PORT)) {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    StarterRequest starterRequest = StarterRequest.parseDelimitedFrom(socket.getInputStream());
                    if (starterRequest.getStart()) {
                        Server server = ServerFactory.createServer(starterRequest.getServerType());
                        server.run();
                    } else {
                        StatResponse statResponse = StatResponse.newBuilder()
                                .addAllValues(new ArrayList<>())
                                .setHandleTime(0)
                                .setResponseTime(0)
                                .build();
                        statResponse.writeDelimitedTo(socket.getOutputStream());
                    }
                }
            }
        }
    }

//    public static void main(String[] args) throws IOException {
//        ServerSocket serverSocket = new ServerSocket(PORT);
//        while (!serverSocket.isClosed()) {
//            Socket socket = serverSocket.accept();
//            try (DataInputStream inputStream = new DataInputStream(socket.getInputStream());
//                 DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
//                Protocol.Config config = Protocol.Config.parseDelimitedFrom(inputStream);
//                final Server server;
//                if (ServerFactory.SERVER_TYPES.contains(config.getServerType()) && config.getRequestsCount() > 0) {
//                    server = ServerFactory.createServer(config.getServerType());
//                } else {
//                    continue;
//                }
//                server.start(SERVER_PORT);
//
//                config = Protocol.Config.parseDelimitedFrom(inputStream);
//                server.close();
//                if (config.getRequestsCount() == 0) {
//                    double defaultTime = -0.03;
//                    if (config.getServerType().equals(ServerFactory.THREAD_PER_CONNECTION_SERVER)) {
//                        defaultTime = -0.01;
//                    } else if (config.getServerType().equals(ServerFactory.THREAD_POOL)) {
//                        defaultTime = -0.02;
//                    }
////                    System.out.println("Times: " + handleTime + " " + responseTime);
//                    Protocol.StatResponse.newBuilder()
//                            .setHandleTime(server.getHandleTime(defaultTime))
//                            .setResponseTime(server.getResponseTime(defaultTime))
//                            .build()
//                            .writeDelimitedTo(outputStream);
//                }
//            }
//        }
//        serverSocket.close();
//    }
}

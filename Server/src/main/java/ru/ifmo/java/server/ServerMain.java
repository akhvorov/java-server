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
                Server server = null;
                try (Socket socket = serverSocket.accept()) {
                    System.out.println("Accept connection");
                    while (!socket.isClosed()) {
                        StarterRequest starterRequest = StarterRequest.parseDelimitedFrom(socket.getInputStream());
                        if (starterRequest == null) {
                            break;
                        }
                        if (starterRequest.getStart()) {
                            System.out.println("Create new server");
                            server = ServerFactory.createServer(starterRequest.getServerType());
                            server.start();
                            StatResponse.newBuilder().build().writeDelimitedTo(socket.getOutputStream());
                        } else {
                            assert server != null;
                            StatResponse.newBuilder()
                                    .addAllValues(new ArrayList<>())
                                    .setHandleTime(server.getHandleTime(-1))
                                    .setResponseTime(server.getResponseTime(-1))
                                    .build()
                                    .writeDelimitedTo(socket.getOutputStream());
                            server.close();
                            System.out.println("Close server");
                            break;
                        }
                    }
                }
            }
        }
    }
}

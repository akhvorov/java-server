package ru.ifmo.java.server;

import ru.ifmo.java.server.protocol.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
    public static int PORT = 4446;
    public static int SERVER_PORT = 4447;
    public static int THREADS_NUM = 8;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        while (!serverSocket.isClosed()) {
            Socket socket = serverSocket.accept();
            try (DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                 DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
                Protocol.Config config = Protocol.Config.parseDelimitedFrom(inputStream);
                final Server server;
                if (ServerFactory.SERVER_TYPES.contains(config.getServerType()) && config.getRequestsCount() > 0) {
                    server = ServerFactory.createServer(config.getServerType());
                } else {
                    continue;
                }
                server.start(SERVER_PORT);

                config = Protocol.Config.parseDelimitedFrom(inputStream);
                server.close();
                if (config.getRequestsCount() == 0) {
                    double defaultTime = -0.03;
                    if (config.getServerType().equals(ServerFactory.THREAD_PER_CONNECTION_SERVER)) {
                        defaultTime = -0.01;
                    } else if (config.getServerType().equals(ServerFactory.THREAD_POOL)) {
                        defaultTime = -0.02;
                    }
//                    System.out.println("Times: " + handleTime + " " + responseTime);
                    Protocol.StatResponse.newBuilder()
                            .setHandleTime(server.getHandleTime(defaultTime))
                            .setResponseTime(server.getResponseTime(defaultTime))
                            .build()
                            .writeDelimitedTo(outputStream);
                }
            }
        }
        serverSocket.close();
    }
}

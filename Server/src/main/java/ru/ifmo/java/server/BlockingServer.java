package ru.ifmo.java.server;

import ru.ifmo.java.server.protocol.Protocol;

import java.io.*;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public abstract class BlockingServer extends Server {
    protected ServerSocket serverSocket;

    @Override
    public void start(int port) throws IOException {
        super.start(port);
        serverSocket = new ServerSocket(port);
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }

    protected List<Integer> readArray(DataInputStream inputStream) throws IOException {
        int size = inputStream.readInt();
        byte[] arrayData = new byte[size];
        int processedBytes = 0;
        while (processedBytes < size) {
            processedBytes += inputStream.read(arrayData, processedBytes, size - processedBytes);
        }
        return new ArrayList<>(Protocol.ArraySortRequest.parseFrom(arrayData).getValuesList());
    }

    protected byte[] prepareArray(List<Integer> array) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(byteArrayOutputStream);

        byte[] sortedArray = Protocol.ArraySortResponse.newBuilder().addAllValues(array).build().toByteArray();
        outputStream.writeInt(sortedArray.length);
        outputStream.write(sortedArray);
        return byteArrayOutputStream.toByteArray();
    }

    protected void writeArray(OutputStream outputStream, List<Integer> list) throws IOException {
        outputStream.write(prepareArray(list));
        outputStream.flush();
    }

//    protected List<Integer> readArray(InputStream inputStream) throws IOException {
//        return new ArrayList<>(Protocol.ArraySortRequest.parseDelimitedFrom(inputStream).getValuesList());
//    }
//
//    protected void writeArray(OutputStream outputStream, List<Integer> list) throws IOException {
//        Protocol.ArraySortResponse.newBuilder().addAllValues(list).build().writeDelimitedTo(outputStream);
//    }
}

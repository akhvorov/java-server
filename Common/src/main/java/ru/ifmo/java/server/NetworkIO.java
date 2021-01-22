package ru.ifmo.java.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class NetworkIO {
    public static byte[] readBytes(InputStream inputStream) throws IOException {
        byte[] s = new byte[4];
        int readedBytes = 0;
        while (readedBytes != 4) {
            readedBytes += inputStream.read(s, readedBytes, s.length - readedBytes);
        }
        int size = 0;
        for (byte b : s) {
            size = size * 256 + b;
        }
        System.out.println("Read " + size + " bytes");
        byte[] bytes = new byte[size];
        int processed = 0;
        while (processed < size) {
            processed += inputStream.read(bytes, processed, size - processed);
        }
        return bytes;
    }

    public static void writeBytes(byte[] request, OutputStream outputStream) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(request.length + 4);
        buffer.putInt(request.length);
        buffer.put(request);
        outputStream.write(buffer.array());
//        outputStream.write(request.length);
//        outputStream.write(request);
        outputStream.flush();
    }
}

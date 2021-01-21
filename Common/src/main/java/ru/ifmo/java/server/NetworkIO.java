package ru.ifmo.java.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class NetworkIO {
    public static byte[] readBytes(InputStream inputStream) throws IOException {
        int size = inputStream.read();
        byte[] bytes = new byte[size];
        int processed = 0;
        while (processed < size) {
            processed += inputStream.read(bytes, processed, size - processed);
        }
        return bytes;
    }

    public static void writeBytes(byte[] request, OutputStream outputStream) throws IOException {
        outputStream.write(request.length);
        outputStream.write(request);
    }
}

package ru.ifmo.java.server;

import com.google.protobuf.InvalidProtocolBufferException;
import ru.ifmo.java.server.protocol.ArraySortRequest;
import ru.ifmo.java.server.protocol.ArraySortResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AsyncServer extends Server {
    private final ExecutorService workersThreadPool;
    private AsynchronousServerSocketChannel serverSocketChannel;

    public AsyncServer(int port, int nThreads) {
        super(port);
        workersThreadPool = Executors.newFixedThreadPool(nThreads);
        try {
            serverSocketChannel = AsynchronousServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                Future<AsynchronousSocketChannel> future = serverSocketChannel.accept();
                AsynchronousSocketChannel socketChannel = future.get();
                Client client = new Client(socketChannel);
                socketChannel.read(client.inputBuffer, client, new CompletionHandler<Integer, Client>() {
                    @Override
                    public void completed(Integer result, Client attachment) {
                        if (attachment.process(result)) {
                            socketChannel.read(client.inputBuffer, client, this);
                        }
                    }

                    @Override
                    public void failed(Throwable exc, Client attachment) {
                        exc.printStackTrace();
                    }
                });
            }
        } catch (InterruptedException | ExecutionException e) {
//            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        workersThreadPool.shutdown();
        serverSocketChannel.close();
    }

    class Client {
        private final AsynchronousSocketChannel socketChannel;
        private ByteBuffer inputBuffer = ByteBuffer.allocate(4);
        private Task task;

        Client(AsynchronousSocketChannel socketChannel) {
            this.socketChannel = socketChannel;
        }

        public boolean process(int nBytes) {
            if (nBytes == -1) return false;
            if (nBytes == 0) return true;
            if (task == null) {
                task = new Task();
                if (inputBuffer.position() >= 4) {
                    inputBuffer.flip();
                    task.size = inputBuffer.getInt();
                    nBytes -= 4;
                    byte[] bytes = inputBuffer.array();
                    ByteBuffer b = ByteBuffer.allocate(task.size);
                    byte[] content = Arrays.copyOfRange(bytes, 4, nBytes + 4);
                    assert content.length <= task.size;
                    b.put(content);
                    inputBuffer = b;
                }
            }
            task.readedBytes += nBytes;
            if (task.readedBytes == task.size) {
                try {
                    inputBuffer.flip();
                    task.arraySortRequest = ArraySortRequest.parseFrom(inputBuffer);
                    workersThreadPool.submit(task);
                    task = null;
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                inputBuffer.clear();
            }
            return true;
        }

        class Task implements Runnable {
            private final TimeMeasurer.Timer timer = responseTimeMeasure.startNewTimer();
            private int size = -1;
            private int readedBytes = 0;
            private ArraySortRequest arraySortRequest;
            private ByteBuffer outBuffer;

            @Override
            public void run() {
                List<Integer> list = new ArrayList<>(arraySortRequest.getValuesList());
                sort(list);
                ArraySortResponse response = ArraySortResponse.newBuilder().addAllValues(list).build();
                outBuffer = ByteBuffer.allocate(response.getSerializedSize() + 4);
                outBuffer.putInt(response.getSerializedSize());
                outBuffer.put(response.toByteArray());
                outBuffer.flip();
                socketChannel.write(outBuffer, this, new CompletionHandler<Integer, Task>() {
                    @Override
                    public void completed(Integer result, Task attachment) {
                        if (outBuffer.hasRemaining()) {
                            socketChannel.write(outBuffer, Task.this, this);
                        } else {
                            timer.stop();
                        }
                    }

                    @Override
                    public void failed(Throwable exc, Task attachment) {
                        exc.printStackTrace();
                    }
                });
            }
        }
    }
}

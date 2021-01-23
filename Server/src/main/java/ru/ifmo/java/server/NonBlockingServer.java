package ru.ifmo.java.server;

import com.google.protobuf.InvalidProtocolBufferException;
import ru.ifmo.java.server.protocol.ArraySortRequest;
import ru.ifmo.java.server.protocol.ArraySortResponse;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NonBlockingServer extends Server {
    private final Selector readSelector;
    private final Selector writeSelector;
    private final ExecutorService selectorPool = Executors.newSingleThreadExecutor();
    private final ExecutorService workersThreadPool;
    private ServerSocketChannel serverSocketChannel;

    public NonBlockingServer(int port, int nThreads) throws IOException {
        super(port);
        readSelector = Selector.open();
        writeSelector = Selector.open();
        workersThreadPool = Executors.newFixedThreadPool(nThreads);
        selectorPool.submit(new IOWorker());
    }

    @Override
    public void run() {
        try {
            serverSocketChannel = ServerSocketChannel.open();  // blocking
//            serverSocketChannel.socket().bind(new InetSocketAddress(port));
            serverSocketChannel.bind(new InetSocketAddress(port));
//            serverSocketChannel.configureBlocking(false);  // non blocking
            while (true) {
                SocketChannel socketChannel = serverSocketChannel.accept();
                socketChannel.configureBlocking(false);
                Client client = new Client(socketChannel);
//                synchronized (readSelector) {
                    socketChannel.register(readSelector, SelectionKey.OP_READ, client);
//                socketChannel.register(writeSelector, SelectionKey.OP_WRITE, client);
                    readSelector.wakeup();
//                writeSelector.wakeup();
//                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        workersThreadPool.shutdown();
        selectorPool.shutdown();
        serverSocketChannel.close();
    }

    enum ClientOutState {
        NEW,
        WAITING
    }

    class Client {
//        private final int id = clientsCnt.getAndIncrement();
        private final SocketChannel channel;
        private final ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
        private final Queue<Task> handledTasks;
        private ClientOutState clientOutState = ClientOutState.WAITING;
        private TimeMeasurer.Timer timer;

        private ByteBuffer inputBuffer;
        private int size = -1;
        private int readedBytes = 0;

        public Client(SocketChannel channel) {
            this.channel = channel;
            handledTasks = new ArrayDeque<>();
        }

        public void read() throws IOException {
            if (timer == null) {
                timer = responseTimeMeasure.startNewTimer();
            }
            if (size == -1) {
                boolean hasSize = channel.read(sizeBuffer) == 4;
                if (hasSize) {
                    sizeBuffer.flip();
                    size = sizeBuffer.getInt();
                    inputBuffer = ByteBuffer.allocate(size);
                    sizeBuffer.clear();
                }
            } else if (readedBytes < size) {
                readedBytes += channel.read(inputBuffer);
                assert readedBytes > size;
            }
        }

        public boolean inputIsReady() {
            return readedBytes == size;
        }

        class Task implements Runnable {
//            private final int id = queryCnt.getAndIncrement();
            private final TimeMeasurer.Timer timer;
            private ByteBuffer outBuffer;

            public Task(TimeMeasurer.Timer timer) {
                this.timer = timer;
            }

            @Override
            public void run() {
                try {
                    inputBuffer.flip();
                    ArraySortRequest arraySortRequest = ArraySortRequest.parseFrom(inputBuffer);
                    inputBuffer.clear();
                    size = -1;
                    readedBytes = 0;

                    List<Integer> list = new ArrayList<>(arraySortRequest.getValuesList());
                    sort(list);
                    ArraySortResponse response = ArraySortResponse.newBuilder().addAllValues(list).build();
                    outBuffer = ByteBuffer.allocate(response.getSerializedSize() + 4);
                    outBuffer.putInt(response.getSerializedSize());
                    outBuffer.put(response.toByteArray());
                    outBuffer.flip();
                    handledTasks.add(this);
                    if (clientOutState == ClientOutState.WAITING) {
                        clientOutState = ClientOutState.NEW;
                        try {
                            channel.register(writeSelector, SelectionKey.OP_WRITE, Client.this);
                        } catch (ClosedChannelException e) {
                            e.printStackTrace();
                        }
                        writeSelector.wakeup();
                    }
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
            }
        }

        public Runnable createTask() {
            assert timer != null;
            Task task = new Task(timer);
            timer = null;
            return task;
        }

        public void write() throws IOException {
            Task task = handledTasks.peek();
            if (task != null) {
                channel.write(task.outBuffer);
                if (!task.outBuffer.hasRemaining()) {
                    task.timer.stop();
                    handledTasks.remove();
                }
            }
        }
    }

    class IOWorker implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    read();
                    write();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void read() throws IOException {
            readSelector.select(100);
            Iterator<SelectionKey> selectedKeys = readSelector.selectedKeys().iterator();
            while (selectedKeys.hasNext()) {
                SelectionKey selectionKey = selectedKeys.next();
                if (selectionKey.isReadable()) {
                    Client client = (Client) selectionKey.attachment();
                    client.read();
                    if (client.inputIsReady()) {
                        workersThreadPool.submit(client.createTask());
                    }
                }
                selectedKeys.remove();
            }
        }

        private void write() throws IOException {
            writeSelector.select(100);
            Iterator<SelectionKey> selectedKeys = writeSelector.selectedKeys().iterator();
            while (selectedKeys.hasNext()) {
                SelectionKey selectionKey = selectedKeys.next();
                if (selectionKey.isWritable()) {
                    Client client = (Client) selectionKey.attachment();
                    if (client.handledTasks.isEmpty()) {
                        client.clientOutState = ClientOutState.WAITING;
                        selectionKey.cancel();
                        selectedKeys.remove();
                        continue;
                    }
                    client.write();
                }
                selectedKeys.remove();
            }
        }
    }
}

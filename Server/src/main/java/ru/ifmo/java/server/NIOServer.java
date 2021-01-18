package ru.ifmo.java.server;

import com.google.protobuf.InvalidProtocolBufferException;
import ru.ifmo.java.server.protocol.ArraySortRequest;
import ru.ifmo.java.server.protocol.ArraySortResponse;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NIOServer extends Server {
    private final Selector readSelector;
    private final Selector writeSelector;
    private final ExecutorService selectorPool = Executors.newSingleThreadExecutor();
    private final ExecutorService workersThreadPool;
    private ServerSocketChannel serverSocketChannel;
    private final List<SocketChannel> activeChannels = new CopyOnWriteArrayList<>();

    public NIOServer(int port, int nThreads) throws IOException {
        super(port);
        readSelector = Selector.open();
        writeSelector = Selector.open();
        workersThreadPool = Executors.newFixedThreadPool(nThreads);
        selectorPool.submit(new IOWorker());
    }

    @Override
    public void run() {
        try {
            System.out.println("Run server");
            serverSocketChannel = ServerSocketChannel.open();  // blocking
            serverSocketChannel.socket().bind(new InetSocketAddress(port));
//            serverSocketChannel.configureBlocking(false);  // non blocking
            while (true) {
                System.out.println("Wait for channel");
                SocketChannel socketChannel = serverSocketChannel.accept();
                System.out.println("Accept channel");
                socketChannel.configureBlocking(false);
                Client client = new Client(socketChannel);
                socketChannel.register(readSelector, SelectionKey.OP_READ, client);
//                socketChannel.register(writeSelector, SelectionKey.OP_WRITE, client);
                System.out.println("Register channel");
                readSelector.wakeup();
//                writeSelector.wakeup();
                activeChannels.add(socketChannel);
                System.out.println("End handle in main server");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        workersThreadPool.shutdown();
        selectorPool.shutdown();
        for (SocketChannel sc : activeChannels) {
            sc.close();
        }
        serverSocketChannel.close();
    }

    enum ClientOutState {
        NEW,
        WORKING,
        WAITING
    }

    class Client {
        private final SocketChannel channel;
        private final ByteBuffer inputBuffer = ByteBuffer.allocate(10000);
        private ArraySortRequest arraySortRequest;
        private final Queue<ByteBuffer> outputBuffers;
        private ClientOutState clientOutState;
        private TimeMeasurer.Timer timer;

        public Client(SocketChannel channel) {
            this.channel = channel;
            outputBuffers = new ArrayDeque<>();
        }

        public void read() throws IOException {
            if (timer == null) {
                timer = responseTimeMeasure.startNewTimer();
            }
            channel.read(inputBuffer);
        }

        public boolean inputIsReady() {
            try {
                inputBuffer.flip();
                arraySortRequest = ArraySortRequest.parseFrom(inputBuffer);
                inputBuffer.clear();
                return true;
            } catch (InvalidProtocolBufferException e) {
                return false;
            }
        }

        class Task implements Runnable {
            private final TimeMeasurer.Timer timer;

            public Task(TimeMeasurer.Timer timer) {
                this.timer = timer;
            }
            // Timer
            // info for identification, when we start read request and write response

            @Override
            public void run() {
                List<Integer> list = new ArrayList<>(arraySortRequest.getValuesList());
                sort(list);
                ArraySortResponse response = ArraySortResponse.newBuilder().addAllValues(list).build();
                ByteBuffer outBuffer = ByteBuffer.allocate(response.getSerializedSize() + 100);
                outputBuffers.add(outBuffer);
                if (clientOutState == ClientOutState.WAITING) {
                    clientOutState = ClientOutState.NEW;
                    try {
                        channel.register(writeSelector, SelectionKey.OP_WRITE, Client.this);
                    } catch (ClosedChannelException e) {
                        e.printStackTrace();
                    }
                    writeSelector.wakeup();
                }
            }
        }

        public Runnable createTask() {
            assert timer != null;
            Task task = new Task(timer);
            timer = null;
            inputBuffer.clear();
            return task;
        }

        public void write() throws IOException {
            ByteBuffer buffer = outputBuffers.peek();
            if (buffer != null) {
                channel.write(buffer);
                if (!buffer.hasRemaining()) {
                    outputBuffers.remove();
                }
            }
        }
    }

    class IOWorker implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    System.out.println("After read");
                    read();
                    System.out.println("After write");
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
                    if (client.outputBuffers.isEmpty()) {
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

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

    //    @Override
//    public void close() throws IOException {
//        try {
//            serverThread.join(50);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        if (serverSocketChannel != null) {
//            serverSocketChannel.close();
//            serverSocketChannel.socket().close();
//        }
//
//        for (SocketChannel channel : activeChannels) {
//            channel.close();
//        }
//
//        threadPool.shutdown();
//        serverThread.interrupt();
//    }

//    private void read(SelectionKey key) throws IOException {
//        SocketChannel channel = (SocketChannel) key.channel();
//        checkContext(key);
//
//        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
//        switch (attachment.state) {
//            case READ_SIZE:
//                int readCount = channel.read(attachment.sizeBuffer);
//                if (readCount == -1) {
//                    key.cancel();
//                    channel.close();
//                }
//
//                if (readCount > 0 && attachment.responseTimer == null) {
//                    attachment.responseTimer = responseTimeMeasure.startNewTimer();
//                }
//
//                if (attachment.sizeBuffer.hasRemaining()) {
//                    break;
//                }
//
//                attachment.sizeBuffer.flip();
//                int size = attachment.sizeBuffer.getInt();
//                attachment.dataBuffer = ByteBuffer.allocate(size);
//                attachment.state = AttachmentState.READ_DATA;
//            case READ_DATA:
//                channel.read(attachment.dataBuffer);
//                if (attachment.dataBuffer.hasRemaining()) {
//                    break;
//                }
//                attachment.state = AttachmentState.PROCEED;
//                threadPool.execute(() -> {
//                    byte[] data = (byte[]) attachment.dataBuffer.flip().array();
//                    try {
//                        Protocol.ArraySortRequest message = Protocol.ArraySortRequest.parseFrom(data);
//                        List<Integer> list = new ArrayList<>(message.getValuesList());
//                        sort(list);
//                        byte[] answer = Protocol.ArraySortResponse.newBuilder().addAllValues(list).build().toByteArray();
//                        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).putInt(answer.length);
//                        sizeBuffer.flip();
//                        attachment.answer = new ByteBuffer[]{sizeBuffer, ByteBuffer.wrap(answer)};
//                        attachment.state = AttachmentState.WRITE;
//                    } catch (InvalidProtocolBufferException e) {
//                        e.printStackTrace();
//                    }
//                });
//            default:
//                break;
//        }
//    }
//
//    private void checkContext(SelectionKey key) {
//        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
//        if (attachment == null || attachment.state == AttachmentState.DONE) {
//            ChannelAttachment newAttachment = new ChannelAttachment();
//            key.attach(newAttachment);
//        }
//    }
//
//    private void write(SelectionKey key) throws IOException {
//        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
//        if (attachment.state == AttachmentState.WRITE) {
//            SocketChannel channel = (SocketChannel) key.channel();
//            if (attachment.answer[1].hasRemaining()) {
//                channel.write(attachment.answer);
//            }
//
//            if (!attachment.answer[1].hasRemaining()) {
//                attachment.responseTimer.stop();
//                attachment.state = AttachmentState.DONE;
//            }
//        }
//    }
//
//    private void accept(SelectionKey key) throws IOException {
//        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
//        channel.configureBlocking(false);
//        channel.socket().setTcpNoDelay(true);
//        ChannelAttachment attachment = new ChannelAttachment();
//        attachment.responseTimer = responseTimeMeasure.startNewTimer();
//        channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, attachment);
//        activeChannels.add(channel);
//    }
//
//    private enum AttachmentState {
//        READ_SIZE, READ_DATA, PROCEED, WRITE, DONE
//    }
//
//    private static class ChannelAttachment {
//        TimeMeasurer.Timer responseTimer;
//        ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
//        ByteBuffer dataBuffer;
//        ByteBuffer[] answer;
//        AttachmentState state = AttachmentState.READ_SIZE;
//    }
}

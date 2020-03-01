package ru.ifmo.java.server;

import com.google.protobuf.InvalidProtocolBufferException;
import ru.ifmo.java.server.protocol.Protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NonBlockingServer extends Server {
    private Selector selector;
    private ExecutorService threadPool;
    private ServerSocketChannel socketChannel;
    private List<SocketChannel> activeChannels = new CopyOnWriteArrayList<>();

    public NonBlockingServer(int threadsNum) {
        threadPool = Executors.newFixedThreadPool(threadsNum);
    }

    @Override
    public void start(int port) throws IOException {
        super.start(port);
        serverThread.start();
    }

    @Override
    public void close() throws IOException {
        if (socketChannel != null) {
            socketChannel.close();
            socketChannel.socket().close();
        }

        for (SocketChannel channel : activeChannels) {
            channel.close();
        }

        threadPool.shutdown();
        serverThread.interrupt();
    }

    @Override
    public void run() {
        try {
            selector = Selector.open();

            socketChannel = ServerSocketChannel.open();
            socketChannel.bind(new InetSocketAddress(port));
            socketChannel.configureBlocking(false);

            socketChannel.register(selector, SelectionKey.OP_ACCEPT);
            while (socketChannel.isOpen()) {
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    if (key.isValid() && key.isAcceptable()) {
                        accept(key);
                    }

                    if (key.isValid() && key.isReadable()) {
                        read(key);
                    }

                    if (key.isValid() && key.isWritable()) {
                        write(key);
                    }

                    keyIterator.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        checkContext(key);

        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        switch (attachment.state) {
            case READ_SIZE:
                int readCount = channel.read(attachment.sizeBuffer);
                if (readCount == -1) {
                    key.cancel();
                    channel.close();
                }

                if (readCount > 0 && attachment.responseTimer == null) {
                    attachment.responseTimer = responseTimeMeasure.startNewTimer();
                }

                if (attachment.sizeBuffer.hasRemaining()) {
                    break;
                }

                attachment.sizeBuffer.flip();
                int size = attachment.sizeBuffer.getInt();
                attachment.dataBuffer = ByteBuffer.allocate(size);
                attachment.state = AttachmentState.READ_DATA;
            case READ_DATA:
                channel.read(attachment.dataBuffer);
                if (attachment.dataBuffer.hasRemaining()) {
                    break;
                }
                attachment.state = AttachmentState.PROCEED;
                threadPool.execute(() -> {
                    byte[] data = (byte[]) attachment.dataBuffer.flip().array();
                    try {
                        Protocol.ArraySortRequest message = Protocol.ArraySortRequest.parseFrom(data);
                        List<Integer> list = new ArrayList<>(message.getValuesList());
                        sort(list);
                        byte[] answer = Protocol.ArraySortResponse.newBuilder().addAllValues(list).build().toByteArray();
                        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).putInt(answer.length);
                        sizeBuffer.flip();
                        attachment.answer = new ByteBuffer[]{sizeBuffer, ByteBuffer.wrap(answer)};
                        attachment.state = AttachmentState.WRITE;
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                });
            default:
                break;
        }
    }

    private void checkContext(SelectionKey key) {
        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        if (attachment == null || attachment.state == AttachmentState.DONE) {
            ChannelAttachment newAttachment = new ChannelAttachment();
            key.attach(newAttachment);
        }
    }

    private void write(SelectionKey key) throws IOException {
        ChannelAttachment attachment = (ChannelAttachment) key.attachment();
        if (attachment.state == AttachmentState.WRITE) {
            SocketChannel channel = (SocketChannel) key.channel();
            if (attachment.answer[1].hasRemaining()) {
                channel.write(attachment.answer);
            }

            if (!attachment.answer[1].hasRemaining()) {
                attachment.responseTimer.stop();
                attachment.state = AttachmentState.DONE;
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        channel.socket().setTcpNoDelay(true);
        ChannelAttachment attachment = new ChannelAttachment();
        attachment.responseTimer = responseTimeMeasure.startNewTimer();
        channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, attachment);
        activeChannels.add(channel);
    }

    private enum AttachmentState {
        READ_SIZE, READ_DATA, PROCEED, WRITE, DONE
    }

    private static class ChannelAttachment {
        TimeMeasurer.Timer responseTimer;
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
        ByteBuffer dataBuffer;
        ByteBuffer[] answer;
        AttachmentState state = AttachmentState.READ_SIZE;
    }
}

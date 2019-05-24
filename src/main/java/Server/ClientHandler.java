package Server;

import CommunicateException.UnReadableException;
import CommunicateException.UnWritableException;
import Utils.Close;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

class ClientHandler {

    private SocketChannel client;
    private ReadHandler readHandler;
    private WriteHandler writeHandler;
    private CallBack callBack;

    ClientHandler(SocketChannel client, CallBack callBack) throws IOException {//外层类仅做初始化和相关操作，不进行线程的逻辑

        this.client = client;
        this.callBack = callBack;

        client.configureBlocking(false);

        Selector readSelector = Selector.open();
        client.register(readSelector, SelectionKey.OP_READ);
        this.readHandler = new ReadHandler(readSelector);


        Selector writeSelector = Selector.open();
        client.register(writeSelector, SelectionKey.OP_WRITE);
        this.writeHandler = new WriteHandler(writeSelector);

    }

    void write(String message) throws IOException {
        writeHandler.write(message);
    }

    void read() {
        Thread readThread = new Thread(readHandler);
        readThread.start();
    }

    void exit() {

        readHandler.exit();
        writeHandler.exit();
        Close.close(client);
        callBack.closeClient(this);
        System.out.println("客户端已经退出");
    }

    private class ReadHandler implements Runnable {

        private Selector readSelector;
        private ByteBuffer byteBuffer;

        ReadHandler(Selector selector) {
            this.readSelector = selector;
            this.byteBuffer = ByteBuffer.allocate(256);
        }

        @Override
        public void run() {
            try {
                //noinspection InfiniteLoopStatement
                while (readSelector.isOpen()) {
                    if (readSelector.select() == 0) {
                        continue;
                    }
                    Iterator<SelectionKey> iterator = readSelector.selectedKeys().iterator();

                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        if (key.isReadable()) {
                            SocketChannel socketChannel = (SocketChannel) key.channel();
                            byteBuffer.clear();
                            int read;
                            if ((read = socketChannel.read(byteBuffer)) > 0) {
                                String message = new String(byteBuffer.array(), 0, read - 1);
                                callBack.onNewMessageArrived(message, ClientHandler.this);
                                if(message.equalsIgnoreCase("bye")){

                                }
                            } else {
                                ClientHandler.this.exit();
                                throw new UnReadableException("客户端不可读");
                            }
                        }
                    }
                }
            } catch (IOException | UnReadableException e) {
                e.printStackTrace();
            } finally {
                exit();
            }
        }

        void exit() {
            Close.close(readSelector);
        }
    }

    private class WriteHandler {//不需要负责消息的来源，直接将消息进行转发

        Selector writeSelector;
        ByteBuffer byteBuffer;

        WriteHandler(Selector writeSelector) {
            this.writeSelector = writeSelector;
            byteBuffer = ByteBuffer.allocate(256);
        }

        void write(String message) throws IOException {
            Writer writer = new Writer(message);
            Thread t = new Thread(writer);
            t.start();
        }

        void exit() {
            Close.close(writeSelector);
        }

        class Writer implements Runnable {

            String message;
            Selector writeSelector;//TODO:可能是多余的
            ByteBuffer byteBuffer;

            Writer(String message) throws IOException {
                this.message = message + '\n';
                writeSelector = Selector.open();
                byteBuffer = ByteBuffer.allocate(256);
            }

            @Override
            public void run() {
                if (!writeSelector.isOpen()) {
                    return;
                }
                byteBuffer.clear();
                byteBuffer.put(message.getBytes());
                byteBuffer.flip();

                while (writeSelector.isOpen() && byteBuffer.hasRemaining()) {
                    try {
                        int len = client.write(byteBuffer);
                        if (len < 0) {//抛异常
                            ClientHandler.this.exit();
                            throw new UnWritableException("客户端不可读");
                        }
                    } catch (IOException | UnWritableException e) {
                        e.printStackTrace();
                    }
                }

            }

        }
    }

}
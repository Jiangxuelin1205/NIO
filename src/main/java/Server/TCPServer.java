package Server;


import Utils.Close;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class TCPServer implements CallBack {

    private final List<ClientHandler> clientList;
    private Listener listener;
    private final ExecutorService e;
    private Selector selector;
    private ServerSocketChannel server;

    TCPServer(int port) throws IOException {
        listener=new Listener();
        clientList = new ArrayList<>();
        e = Executors.newSingleThreadExecutor();
        selector = Selector.open();
        server = ServerSocketChannel.open();
        server.configureBlocking(false);
        server.socket().bind(new InetSocketAddress(port));
        server.register(selector, SelectionKey.OP_ACCEPT);
    }

    void start() {
        Thread t = new Thread(listener);
        t.start();
    }

    public void stop() {
        if (listener != null) {
            listener.exit();
        }

        Close.close(selector);
        Close.close(server);

        for (ClientHandler clientHandler : clientList) {
            clientHandler.exit();
        }
        clientList.clear();
        e.shutdownNow();
    }

    public void broadcast(String message) throws IOException {
        for (ClientHandler client : clientList) {
            client.write(message);
        }
    }

    @Override
    public void onNewMessageArrived(String message, ClientHandler handler) {
        e.execute(() -> {
            synchronized (TCPServer.this) {
                for (ClientHandler clientHandler : clientList) {
                    //if (clientHandler != handler) {
                    try {
                        clientHandler.write(message);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                        // }
                    }
                }
            }
        });
    }

    @Override
    public void closeClient(ClientHandler client) {
        synchronized (this) {
            clientList.remove(client);
        }
    }

    private class Listener implements Runnable {

        @Override
        public void run() {
            System.out.println("服务器准备就绪");
            try {
                //noinspection InfiniteLoopStatement
                while (true) {
                    if (selector.select() == 0) {
                        continue;
                    }
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();

                        if (key.isAcceptable()) {
                            ServerSocketChannel server = (ServerSocketChannel) key.channel();
                            SocketChannel client = server.accept();//服务器等待客户端，该方法阻塞

                            ClientHandler clientHandler = new ClientHandler(client, TCPServer.this);
                            synchronized (TCPServer.this) {
                                clientList.add(clientHandler);
                            }
                            clientHandler.read();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        void exit() {
            try {
                server.close();
                selector.wakeup();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}

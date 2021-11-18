import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class NioDummyServer {
    public static void main(String[] args)
    throws Exception {
        int port = Integer.parseInt(args[0]);

        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        ssc.bind(new InetSocketAddress(port));
        System.out.printf("[bind port=%d]%n", port);

        Selector selector = Selector.open();
        ssc.register(selector, SelectionKey.OP_ACCEPT).attach(new AcceptHandler());

        for (;;) {
            int nReady = selector.select(1000);
            if (nReady > 0) {
                for (SelectionKey readyKey : selector.selectedKeys()) {
                    Handler handler = (Handler)readyKey.attachment();
                    if (handler == null) {
                        readyKey.cancel();
                        readyKey.channel().close();
                    }
                    else {
                        handler.handle(readyKey);
                    }
                }
                selector.selectedKeys().clear();
            }
        }
    }

    abstract static class Handler {
        abstract void handle(SelectionKey key)
        throws IOException;
    }

    static class AcceptHandler extends Handler {
        @Override
        void handle(SelectionKey key)
        throws IOException {
            System.out.printf("[selected acceptor]%n");
            ServerSocketChannel ssc = (ServerSocketChannel)key.channel();
            if (!key.isValid()) {
                ssc.close();
            }
            else if (key.isAcceptable()) {
                SocketChannel ch = ssc.accept();
                if (ch != null) {
                    ch.configureBlocking(false);
                    IOHandler handler = new IOHandler();
                    ch.register(key.selector(), SelectionKey.OP_READ).attach(handler);
                    System.out.printf(
                        "[accept ch=%d local=%s remote=%s]%n",
                        handler.chId,
                        ch.getLocalAddress(),
                        ch.getRemoteAddress());
                }
            }
        }
    }

    static class IOHandler extends Handler {
        static int chIdCounter;
        final int chId = chIdCounter++;
        ByteBuffer input = ByteBuffer.allocateDirect(4*1024);
        ByteBuffer output = ByteBuffer.allocateDirect(4*1024);
        boolean foundEndOfHeaders;
        int eol = 0;

        public IOHandler() {
        }

        @Override
        void handle(SelectionKey key)
        throws IOException {
            System.out.printf("[selected ch=%d]%n", chId);
            SocketChannel ch = (SocketChannel)key.channel();
            if (!key.isValid()) {
                ch.close();
                System.out.printf("[close ch=%d]%n", chId);
            }
            else {
                if (key.isReadable()) {
                    System.out.printf("[read ch=%d]%n", chId);
                    int nBytes;
                    while ((nBytes = ch.read(input)) != 0) {
                        if (nBytes == -1) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                            break;
                        }
                        input.flip();
                        while (input.hasRemaining()) {
                            byte b = input.get();
                            System.out.write(b & 0xFF);

                            if (!foundEndOfHeaders) {
                                // end of headers is marked by "\r\n\r\n" sequence
                                if (b == '\r') {
                                    continue;
                                }
                                else if (b == '\n') {
                                    eol++;
                                }
                                else {
                                    eol = 0;
                                }
                                if (eol == 2) {
                                    foundEndOfHeaders = true;
                                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                                    byte[] body = "Test successful\r\n".getBytes(Charset.forName("US-ASCII"));
                                    byte[] headers = (
                                        "HTTP/1.0 418 I'm a teapot\r\n" +
                                        "Content-Length: " + body.length + "\r\n" +
                                        "\r\n").getBytes(Charset.forName("US-ASCII"));
                                    output.put(headers);
                                    output.put(body);
                                    output.flip();
                                }
                            }
                        }
                        input.clear();
                    }
                }
                if ((key.interestOps() & SelectionKey.OP_WRITE) != 0 && key.isWritable()) {
                    System.out.printf("[write ch=%d]%n", chId);
                    while (output.hasRemaining() && ch.write(output) != 0) {
                    }
                    if (!output.hasRemaining()) {
                        key.cancel();
                        ch.close();
                        System.out.printf("[close ch=%d]%n", chId);
                    }
                }
            }
        }
    }
}

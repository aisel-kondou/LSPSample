package sample;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LSPClient {

    private static final int LISTEN_PORT = 1055;
    private static final int BUFFER_SIZE = 4096;

    private static final String LS_ECLIPSE = "Eclipse JDT Language Server";

    // Path to target files
    private static final Path WORKSPACE = Paths.get("C:\\Eclipse\\Workspace");
    private static final Path PROJECT_NAME = Paths.get("sample");
    private static final Path PATH_TO_SRC = Paths.get("src\\main\\java");
    private static final Path PACKAGE = Paths.get("test.sample".replace('.', '/'));

    private ServerSocketChannel serverChannel;
    private Selector selector;

    public static void main(String[] args) {
        LSPClient app = new LSPClient();
        app.setUp();

        System.out.println("bye...");
    }

    void setUp() {
        // start event loop for user-input from System.in
        new Thread(eventLoop).start();

        // start LSP Client
        startClient();
    }

    public void startClient() {
        try {
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(LISTEN_PORT));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("-- Start");
            while (true) {
                while (selector.select() > 0) {
                    for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
                        SelectionKey key = (SelectionKey)it.next();
                        it.remove();
                        if (key.isAcceptable()) {
                            doAccept((ServerSocketChannel)key.channel());

                            doInitialize();
                        } else if (key.isReadable()) {
                            SocketChannel channel = (SocketChannel)key.channel();
                            doRead(channel);
                        } else if (key.isWritable()) {
                            SocketChannel channel = (SocketChannel)key.channel();
                            doWrite(channel);
                        }
                    }
                }
                if (!selector.isOpen()) {
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    //

    private void doAccept(ServerSocketChannel serverChannel) {
        try {
            SocketChannel channel = serverChannel.accept();
            String remoteAddress = channel.socket().getRemoteSocketAddress().toString();
            System.out.println("-- Connected:" + remoteAddress);

            channel.configureBlocking(false);

            if (!channelTable.containsKey(LS_ECLIPSE)) {
                channelTable.put(LS_ECLIPSE, channel);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //

    /**
     * buffer for reading from SocketChannel
     */
    private ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    /**
     * chunks of message("Content-Length: ..\r\n\r\n{..}")s
     */
    private CharBuffer chunks = CharBuffer.allocate(BUFFER_SIZE);

    private Charset UTF8 = Charset.forName("UTF-8");

    private void doRead(SocketChannel channel) {
        try {
            if (channel.read(readBuffer) < 0) {
                return;
            }
            readBuffer.flip();

            chunks.put(UTF8.decode(readBuffer));
            chunks.flip();

            readBuffer.clear();

            while (chunks.length() > 0) {
                Pattern p = Pattern.compile("Content-Length: ([0-9]+)\\r\\n");
                Matcher m = p.matcher(chunks);

                if (m.find()) {
                    int bodyLen = Integer.valueOf(m.group(1));
                    String header = String.format("Content-Length: %s\r\n", bodyLen);
                    int headerLen = header.length();
                    int totalLen = headerLen +2 +bodyLen; // +2 is the length of "\r\n" between header and body
                    if (chunks.length() >= totalLen) {
                        char[] msg = new char[totalLen];
                        chunks.get(msg);

                        String message = new String(msg);

                        printServerMessage(message);
                        getResponseQueue().add(message);
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            chunks.compact();

        } catch (IOException e) {
            //e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * print message from server on console.
     *
     * @param message message from server
     */
    private static final void printServerMessage(String message) {
        if (isResponse(message)) {
            System.out.println("<< Response");
        } else {
            System.out.println("<< Notification");
        }
        System.out.println(message);
        System.out.println("----");
    }

    private static final boolean isResponse(String message) {
        // TODO JSONを解析し正しい位置にidが存在するかチェックする必要あり
        // TODO Language Server からのリクエストの可能性もある（methodで判別する）
        return message.indexOf("\"id\"") != -1;
    }

    //

    private void doInitialize() {
        try {
            doRequest(requestInitialize(WORKSPACE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * buffer for writing on SocketChannel
     */
    private ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    private void doWrite(SocketChannel channel) {
        writeBuffer.clear();
        try {
            Queue<String> requests = getRequestQueue();
            while (requests.size() > 0) {
                String message = requests.remove();
                printRequest(message);
                writeBuffer.put(message.getBytes());
                writeBuffer.flip();

                channel.write(writeBuffer);

                writeBuffer.clear();
            }

            channel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            //e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * print message from client on console.
     *
     * @param message message from client
     */
    private static final void printRequest(String message) {
        if (isRequest(message)) {
            System.out.println(">> Request");
        } else {
            System.out.println(">> Notification");
        }
        System.out.println(message);
        System.out.println("----");
    }

    private static final boolean isRequest(String message) {
        // TODO JSONを解析し正しい位置にidが存在するかチェックする必要あり
        return message.indexOf("\"id\"") != -1;
    }

    private String requestInitialize(Path file) {
        final int id = generateId();
        String template = "{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":%d,\"params\":{\"processId\":null,\"rootUri\":\"%s\",\"capabilities\":{\"workspace\":{\"applyEdit\":false}}}}";
        String body = String.format(template, id, file.toUri());
        String request = "Content-Length: %d\r\n\r\n%s";

        return String.format(request, body.length(), body);
    }

    private String requestOrganizeImports(Path file) {
        final int id = generateId();
        String template = "{\"jsonrpc\":\"2.0\",\"method\":\"workspace/executeCommand\",\"id\":%d,\"params\":{\"command\":\"java.edit.organizeImports\",\"arguments\":[\"%s\"]}}";
        String body = String.format(template, id, file.toUri());
        String request = "Content-Length: %d\r\n\r\n%s";

        return String.format(request, body.length(), body);
    }

    private static String notifyExit() {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"exit\",\"id\":%d}";
        String request = "Content-Length: %d\r\n\r\n%s";

        return String.format(request, body.length(), body);
    }

    private AtomicInteger idGen = new AtomicInteger(1);

    private int generateId() {
        return idGen.incrementAndGet();
    }

    private void shutdown() {
        synchronized (serverChannel) {
            try {
                if (serverChannel != null && serverChannel.isOpen()) {
                    System.out.println("-- Stop");
                    serverChannel.close();
                }
                if (selector != null) {
                    selector.close();
                }

                channelTable.values().forEach(channel -> {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    //

    private ConcurrentHashMap<String, SocketChannel> channelTable = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Queue<String>> requestsTable = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Queue<String>> responseTable = new ConcurrentHashMap<>();

    Queue<String> getRequestQueue() {
        Queue<String> queue = requestsTable.get(LS_ECLIPSE);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<String>();
            requestsTable.put(LS_ECLIPSE, queue);
        }

        return queue;
    }

    Queue<String> getResponseQueue() {
        Queue<String> queue = responseTable.get(LS_ECLIPSE);
        if (queue == null) {
            queue = new ConcurrentLinkedQueue<String>();
            responseTable.put(LS_ECLIPSE, queue);
        }

        return queue;
    }

    private void doRequest(String message) throws ClosedChannelException {
        getRequestQueue().add(message);

        if (channelTable.containsKey(LS_ECLIPSE)) {
            SocketChannel channel = channelTable.get(LS_ECLIPSE);
            channel.register(selector, SelectionKey.OP_WRITE);
            selector.wakeup();
        }
    }

    //

    Runnable eventLoop = new Runnable() {

        @Override
        public void run() {
            try {
                while (true) {
                    BufferedReader keyEvent = new BufferedReader(new InputStreamReader(System.in));

                    String line = keyEvent.readLine().trim();
                    if ("exit".equals(line)) {
                        doRequest(notifyExit());
                        Thread.sleep(500);
                        shutdown();
                        break;
                    } else {
                        doRequest(requestOrganizeImports(getFilePath(line)));
                        System.out.println("-- target file:" + line);
                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    private static final Path getFilePath(String fileName) {
        return WORKSPACE.resolve(PROJECT_NAME).resolve(PATH_TO_SRC).resolve(PACKAGE).resolve(fileName);
    }
}

package net.mando.personal;

import java.io.IOException;
import java.net.Socket;

/** TLS close may write close_notify: never perform it on an Android lifecycle thread. */
final class SocketCloser {
    private SocketCloser() {}
    static void close(Socket socket) {
        if (socket == null) return;
        Thread closer = new Thread(() -> {
            try { socket.close(); } catch (IOException ignored) { }
        }, "mando-socket-close");
        closer.setDaemon(true);
        closer.start();
    }
}

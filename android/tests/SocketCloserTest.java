package net.mando.personal;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
public final class SocketCloserTest {
    public static void main(String[] args) throws Exception {
        Thread caller = Thread.currentThread();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Socket fake = new Socket() {
            public void close() throws IOException {
                if (Thread.currentThread() == caller) throw new AssertionError("Network close on caller thread");
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IOException(e); }
                throw new IOException("Expected close failure");
            }
        };
        SocketCloser.close(fake);
        if (!entered.await(2, TimeUnit.SECONDS)) throw new AssertionError("Close not scheduled");
        SocketCloser.close(null);
        release.countDown();
        System.out.println("Socket close off caller thread, nonblocking and null-safe: passed");
    }
}

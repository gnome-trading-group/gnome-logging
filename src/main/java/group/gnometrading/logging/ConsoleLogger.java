package group.gnometrading.logging;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.EpochNanoClock;

/**
 * Writes each line to stdout on the calling thread, in the order it was logged: for backtests and tests, where a
 * line should land beside the simulated events around it. A live process uses {@link AsyncLogger}, which keeps the
 * write off the threads that log.
 */
public final class ConsoleLogger implements Logger {

    private final EpochNanoClock clock;
    private final PrintStream out;
    private final ExpandableArrayBuffer line = new ExpandableArrayBuffer(512);
    private final long[] values = new long[LogFormatter.MAX_ARGS];
    private final ReentrantLock lock = new ReentrantLock();

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public ConsoleLogger(EpochNanoClock clock) {
        this(clock, System.out);
    }

    ConsoleLogger(EpochNanoClock clock, PrintStream out) {
        this.clock = clock;
        this.out = out;
    }

    @Override
    public void log(LogMessage msg) {
        print(msg, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public void log(LogMessage msg, long v1) {
        print(msg, 1, v1, 0, 0, 0, 0, 0);
    }

    @Override
    public void log(LogMessage msg, long v1, long v2) {
        print(msg, 2, v1, v2, 0, 0, 0, 0);
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3) {
        print(msg, 3, v1, v2, v3, 0, 0, 0);
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3, long v4) {
        print(msg, 4, v1, v2, v3, v4, 0, 0);
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3, long v4, long v5) {
        print(msg, 5, v1, v2, v3, v4, v5, 0);
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3, long v4, long v5, long v6) {
        print(msg, 6, v1, v2, v3, v4, v5, v6);
    }

    private void print(LogMessage msg, int count, long v1, long v2, long v3, long v4, long v5, long v6) {
        lock.lock();
        try {
            values[0] = v1;
            values[1] = v2;
            values[2] = v3;
            values[3] = v4;
            values[4] = v5;
            values[5] = v6;
            final int length = LogFormatter.putLine(line, 0, clock.nanoTime(), msg, count, values);
            out.write(line.byteArray(), 0, length);
        } finally {
            lock.unlock();
        }
    }

    /**
     * The message is formatted once and then written as it is: used as a format again, a {@code %} in it (a venue's
     * error text, say) would be read as a specifier and throw. Allocates, as logf's free text always has.
     */
    @Override
    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public void logf(LogMessage msg, String format, Object... args) {
        final byte[] text = String.format(format, args).getBytes(StandardCharsets.UTF_8);
        lock.lock();
        try {
            final int length = LogFormatter.putTextLine(line, 0, clock.nanoTime(), msg, text, text.length);
            out.write(line.byteArray(), 0, length);
        } finally {
            lock.unlock();
        }
    }
}

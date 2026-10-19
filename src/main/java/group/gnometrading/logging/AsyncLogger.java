package group.gnometrading.logging;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.agrona.BitUtil;
import org.agrona.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.EpochNanoClock;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

/**
 * The live logger. A {@link #log} call copies its values into a shared ring and returns: no allocation, no lock and no
 * write on the thread that logs, which is often a trading thread. One background thread turns the records into
 * lines and writes them to stdout in batches.
 *
 * <p>A call that finds the ring full is dropped rather than made to wait, and the drops are reported in a
 * {@link LogMessage#LOG_RECORDS_DROPPED} line. Records still in the ring when the JVM dies abruptly (SIGKILL, the OOM
 * killer) are lost; any normal exit, {@code System.exit} included, drains them first through a shutdown hook. Lines
 * logged after that write straight to stdout, since shutdown hooks run in no fixed order and another hook may still
 * be logging.
 */
public final class AsyncLogger implements Logger, AutoCloseable {

    /** About 14,000 {@link #log} lines. */
    public static final int DEFAULT_CAPACITY = 1 << 20;
    /** A {@link #logf} line's text is cut to this many bytes, so one long message can't take most of the ring. */
    static final int MAX_TEXT_BYTES = 4096;

    private static final int STRUCTURED = 1;
    private static final int TEXT = 2;
    private static final int NANOS_OFFSET = 0;
    private static final int MESSAGE_OFFSET = 8;
    // The argument count for a structured record, the text's length for a text one.
    private static final int COUNT_OFFSET = 12;
    private static final int BODY_OFFSET = 16;
    private static final int STRUCTURED_LENGTH = BODY_OFFSET + LogFormatter.MAX_ARGS * Long.BYTES;
    private static final int READ_LIMIT = 256;
    // Written out early past this, so a burst doesn't grow the batch buffer without bound.
    private static final int FLUSH_THRESHOLD = 64 * 1024;
    private static final long CLOSE_TIMEOUT_MS = 5_000;
    private static final LogMessage[] MESSAGES = LogMessage.values();

    private final EpochNanoClock clock;
    private final PrintStream out;
    private final ManyToOneRingBuffer ring;
    // A small test ring can't take MAX_TEXT_BYTES in one record.
    private final int maxTextBytes;
    private final AtomicLong dropped = new AtomicLong();
    private final ExpandableArrayBuffer batch = new ExpandableArrayBuffer(FLUSH_THRESHOLD + 8 * 1024);
    private final long[] values = new long[LogFormatter.MAX_ARGS];
    // Held once: a method reference evaluated on every drain would allocate a new handler each time.
    private final MessageHandler onRecord = this::onRecord;
    // Guards the batch and the close: taken by the drain thread, close and the writes after it, never by a logging
    // thread while the ring is open.
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Thread drainThread;
    private final Thread shutdownHook;
    private int batchLength;
    private volatile boolean closed;

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public AsyncLogger(EpochNanoClock clock) {
        this(clock, DEFAULT_CAPACITY, System.out, true);
    }

    /** @param start false leaves the ring undrained until {@link #drain} is called, for tests */
    AsyncLogger(EpochNanoClock clock, int capacity, PrintStream out, boolean start) {
        this.clock = clock;
        this.out = out;
        this.ring = new ManyToOneRingBuffer(new UnsafeBuffer(BufferUtil.allocateDirectAligned(
                capacity + RingBufferDescriptor.TRAILER_LENGTH, BitUtil.CACHE_LINE_LENGTH)));
        this.maxTextBytes = Math.min(MAX_TEXT_BYTES, ring.maxMsgLength() - BODY_OFFSET);
        this.drainThread = new Thread(this::runDrain, "gnome-log-drain");
        this.drainThread.setDaemon(true);
        this.shutdownHook = new Thread(this::close, "gnome-log-close");
        if (start) {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            drainThread.start();
        }
    }

    @Override
    public void log(LogMessage msg) {
        record(msg, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public void log(LogMessage msg, long v1) {
        record(msg, 1, v1, 0, 0, 0, 0, 0);
    }

    @Override
    public void log(LogMessage msg, long v1, long v2) {
        record(msg, 2, v1, v2, 0, 0, 0, 0);
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3) {
        record(msg, 3, v1, v2, v3, 0, 0, 0);
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3, long v4) {
        record(msg, 4, v1, v2, v3, v4, 0, 0);
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3, long v4, long v5) {
        record(msg, 5, v1, v2, v3, v4, v5, 0);
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3, long v4, long v5, long v6) {
        record(msg, 6, v1, v2, v3, v4, v5, v6);
    }

    /**
     * Formatted on the calling thread, as logf always has been, then queued as text so it stays in order with the
     * lines around it. Allocates; not for a hot path.
     */
    @Override
    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public void logf(LogMessage msg, String format, Object... args) {
        final long nanos = clock.nanoTime();
        final byte[] text = String.format(format, args).getBytes(StandardCharsets.UTF_8);
        final int length = Math.min(text.length, maxTextBytes);
        if (closed) {
            writeNow(nanos, msg, text, length);
            return;
        }
        final int index = ring.tryClaim(TEXT, BODY_OFFSET + length);
        if (index < 0) {
            dropped.incrementAndGet();
            return;
        }
        final AtomicBuffer buffer = ring.buffer();
        buffer.putLong(index + NANOS_OFFSET, nanos);
        buffer.putInt(index + MESSAGE_OFFSET, msg.ordinal());
        buffer.putInt(index + COUNT_OFFSET, length);
        buffer.putBytes(index + BODY_OFFSET, text, 0, length);
        ring.commit(index);
    }

    private void record(LogMessage msg, int count, long v1, long v2, long v3, long v4, long v5, long v6) {
        final long nanos = clock.nanoTime();
        if (closed) {
            writeNow(nanos, msg, count, v1, v2, v3, v4, v5, v6);
            return;
        }
        final int index = ring.tryClaim(STRUCTURED, STRUCTURED_LENGTH);
        if (index < 0) {
            dropped.incrementAndGet();
            return;
        }
        final AtomicBuffer buffer = ring.buffer();
        buffer.putLong(index + NANOS_OFFSET, nanos);
        buffer.putInt(index + MESSAGE_OFFSET, msg.ordinal());
        buffer.putInt(index + COUNT_OFFSET, count);
        final int body = index + BODY_OFFSET;
        buffer.putLong(body, v1);
        buffer.putLong(body + Long.BYTES, v2);
        buffer.putLong(body + 2 * Long.BYTES, v3);
        buffer.putLong(body + 3 * Long.BYTES, v4);
        buffer.putLong(body + 4 * Long.BYTES, v5);
        buffer.putLong(body + 5 * Long.BYTES, v6);
        ring.commit(index);
    }

    /** Drains what is queued now and writes it out, returning how many records it read. */
    int drain() {
        writeLock.lock();
        try {
            int read = ring.read(onRecord, READ_LIMIT);
            if (read < READ_LIMIT) {
                // One read stops at the end of the buffer, so records that wrapped to its start need a second.
                read += ring.read(onRecord, READ_LIMIT - read);
            }
            final long drops = dropped.get() == 0 ? 0 : dropped.getAndSet(0);
            if (drops > 0) {
                values[0] = drops;
                batchLength = LogFormatter.putLine(
                        batch, batchLength, clock.nanoTime(), LogMessage.LOG_RECORDS_DROPPED, 1, values);
            }
            flush();
            return read;
        } finally {
            writeLock.unlock();
        }
    }

    private void onRecord(int type, DirectBuffer buffer, int index, int length) {
        final long nanos = buffer.getLong(index + NANOS_OFFSET);
        final LogMessage msg = MESSAGES[buffer.getInt(index + MESSAGE_OFFSET)];
        final int count = buffer.getInt(index + COUNT_OFFSET);
        final int body = index + BODY_OFFSET;
        if (type == TEXT) {
            batchLength = LogFormatter.putTextLine(batch, batchLength, nanos, msg, buffer, body, count);
        } else {
            for (int i = 0; i < count; i++) {
                values[i] = buffer.getLong(body + i * Long.BYTES);
            }
            batchLength = LogFormatter.putLine(batch, batchLength, nanos, msg, count, values);
        }
        if (batchLength > FLUSH_THRESHOLD) {
            flush();
        }
    }

    private void flush() {
        if (batchLength > 0) {
            out.write(batch.byteArray(), 0, batchLength);
            out.flush();
            batchLength = 0;
        }
    }

    private void writeNow(long nanos, LogMessage msg, int count, long v1, long v2, long v3, long v4, long v5, long v6) {
        writeLock.lock();
        try {
            values[0] = v1;
            values[1] = v2;
            values[2] = v3;
            values[3] = v4;
            values[4] = v5;
            values[5] = v6;
            batchLength = LogFormatter.putLine(batch, batchLength, nanos, msg, count, values);
            flush();
        } finally {
            writeLock.unlock();
        }
    }

    private void writeNow(long nanos, LogMessage msg, byte[] text, int length) {
        writeLock.lock();
        try {
            batchLength = LogFormatter.putTextLine(batch, batchLength, nanos, msg, text, length);
            flush();
        } finally {
            writeLock.unlock();
        }
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private void runDrain() {
        final IdleStrategy idle = new SleepingMillisIdleStrategy(1);
        while (!closed) {
            try {
                idle.idle(drain());
            } catch (RuntimeException e) { // a line that can't be written must not stop every line after it
                System.err.println("gnome-log-drain: " + e);
            }
        }
    }

    /**
     * Drains everything queued and switches to writing lines straight away. Run by the shutdown hook, so an owner
     * need not call it; safe to call more than once.
     */
    @Override
    public void close() {
        writeLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
        } finally {
            writeLock.unlock();
        }
        if (drainThread.isAlive()) {
            try {
                drainThread.join(CLOSE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        while (drain() > 0) {
            // keep going: a record committed just before closed was set may still be in the ring
        }
        if (Thread.currentThread() != shutdownHook) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) { // the JVM is already shutting down; the hook will find this closed
            }
        }
    }
}

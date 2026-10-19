package group.gnometrading.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsyncLoggerTest {

    private static final int CALLS = 100_000;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    private String written() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void nothingIsWrittenUntilTheRingIsDrained() {
        final AsyncLogger logger = new AsyncLogger(() -> 5, 4096, out, false);

        logger.log(LogMessage.SOCKET_CONNECTED);
        assertEquals("", written());

        logger.drain();
        assertEquals("[5] SOCKET_CONNECTED\n", written());
    }

    @Test
    void structuredAndFormattedLinesKeepTheirOrder() {
        final AsyncLogger logger = new AsyncLogger(() -> 5, 4096, out, false);

        logger.log(LogMessage.ORDER_REJECTED_EXCHANGE_CONSTRAINTS, 814, 100, 'B', 505_000_000L, 10_000_000, 2);
        logger.logf(LogMessage.VENUE_RATE_LIMITED, "venue said: %s", "100% full");
        logger.log(LogMessage.LEDGER_WRITE_FAILED, -1);
        logger.drain();

        assertEquals(
                "[5] ORDER_REJECTED_EXCHANGE_CONSTRAINTS clientOid=814 listing=100 side=BID price=0.505 size=10"
                        + " rule=TICK\n"
                        + "[5] VENUE_RATE_LIMITED venue said: 100% full\n"
                        + "[5] LEDGER_WRITE_FAILED status=-1\n",
                written());
    }

    @Test
    void aFullRingDropsLinesAndSaysHowMany() {
        final AsyncLogger logger = new AsyncLogger(() -> 5, 1024, out, false);

        for (int i = 0; i < 20; i++) {
            logger.log(LogMessage.LEDGER_WRITE_FAILED, i);
        }
        logger.drain();

        final String[] lines = written().split("\n");
        // 72 bytes a record: 14 fit in 1024
        assertEquals(15, lines.length);
        assertEquals("[5] LEDGER_WRITE_FAILED status=13", lines[13]);
        assertEquals("[5] LOG_RECORDS_DROPPED count=6", lines[14]);

        logger.log(LogMessage.LEDGER_WRITE_FAILED, 99);
        logger.drain();
        assertTrue(written().endsWith("count=6\n[5] LEDGER_WRITE_FAILED status=99\n"));
    }

    @Test
    void longFormattedTextIsCut() {
        final AsyncLogger logger = new AsyncLogger(() -> 5, 64 * 1024, out, false);

        logger.logf(LogMessage.DEBUG, "%s", "x".repeat(AsyncLogger.MAX_TEXT_BYTES + 100));
        logger.drain();

        assertEquals("[5] DEBUG " + "x".repeat(AsyncLogger.MAX_TEXT_BYTES) + "\n", written());
    }

    @Test
    void closeDrainsWhatIsQueuedAndLaterLinesAreWrittenStraightAway() {
        final AsyncLogger logger = new AsyncLogger(() -> 5, 4096, out, false);

        logger.log(LogMessage.SOCKET_CONNECTED);
        logger.close();
        assertEquals("[5] SOCKET_CONNECTED\n", written());

        logger.log(LogMessage.SOCKET_DISCONNECTED);
        logger.logf(LogMessage.DEBUG, "after %d", 1);
        assertEquals("[5] SOCKET_CONNECTED\n[5] SOCKET_DISCONNECTED\n[5] DEBUG after 1\n", written());
        logger.close();
    }

    @Test
    void theDrainThreadWritesEveryThreadsLinesInEachThreadsOrder() throws InterruptedException {
        final AsyncLogger logger = new AsyncLogger(() -> 5, AsyncLogger.DEFAULT_CAPACITY, out, true);
        final int threads = 4;
        final int perThread = 2_000;
        final List<Thread> producers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int strategy = t;
            producers.add(new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    logger.log(LogMessage.VENUE_ORDER_UNATTRIBUTABLE_HALTED, strategy, i);
                }
            }));
        }
        producers.forEach(Thread::start);
        for (final Thread producer : producers) {
            producer.join();
        }
        logger.close();

        final int[] next = new int[threads];
        for (final String line : written().split("\n")) {
            final String[] parts = line.split(" ");
            final int strategy = Integer.parseInt(parts[2].substring("strategy=".length()));
            assertEquals(next[strategy]++, Integer.parseInt(parts[3].substring("listing=".length())));
        }
        for (int t = 0; t < threads; t++) {
            assertEquals(perThread, next[t]);
        }
    }

    @Test
    void loggingAllocatesNothingEvenWhenTheRingIsFull() {
        final ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        final AsyncLogger logger = new AsyncLogger(() -> 5, 64 * 1024, out, false);
        for (int i = 0; i < 20_000; i++) {
            logger.log(LogMessage.ORDER_REJECTED_RISK_CHECK, i, 100, 'B', 505_000_000L, 10_000_000, 3);
        }
        logger.drain();

        final long threadId = Thread.currentThread().getId();
        final long baseline = threadBean.getThreadAllocatedBytes(threadId);
        final long overhead = threadBean.getThreadAllocatedBytes(threadId) - baseline;
        final long before = threadBean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < CALLS; i++) {
            logger.log(LogMessage.ORDER_REJECTED_RISK_CHECK, i, 100, 'B', 505_000_000L, 10_000_000, 3);
        }
        // Under a byte a call: any object a call made would be at least 16. Not exactly zero, since the JIT
        // compiling the loop charges a few hundred bytes to this thread once.
        assertTrue(threadBean.getThreadAllocatedBytes(threadId) - before - overhead < CALLS);
    }
}

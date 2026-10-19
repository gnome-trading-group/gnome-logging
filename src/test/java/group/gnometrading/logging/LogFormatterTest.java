package group.gnometrading.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

class LogFormatterTest {

    private static final int CALLS = 100_000;

    private static String line(LogMessage msg, long... values) {
        final ExpandableArrayBuffer out = new ExpandableArrayBuffer(512);
        final long[] padded = new long[LogFormatter.MAX_ARGS];
        System.arraycopy(values, 0, padded, 0, values.length);
        final int length = LogFormatter.putLine(out, 0, 5, msg, values.length, padded);
        return out.getStringWithoutLengthAscii(0, length);
    }

    @Test
    void namesEachArgumentAndPrintsPricesSizesSidesAndCodesReadably() {
        assertEquals(
                "[5] ORDER_REJECTED_EXCHANGE_CONSTRAINTS clientOid=814 listing=100 side=BID price=0.505 size=10"
                        + " rule=TICK\n",
                line(LogMessage.ORDER_REJECTED_EXCHANGE_CONSTRAINTS, 814, 100, 'B', 505_000_000L, 10_000_000, 2));
    }

    @Test
    void aMarketOrdersMissingPriceReadsAsNull() {
        assertEquals(
                "[5] ORDER_REJECTED_RISK_CHECK clientOid=1 listing=100 side=ASK price=null size=0.000001"
                        + " policyId=37\n",
                line(LogMessage.ORDER_REJECTED_RISK_CHECK, 1, 100, 'A', Long.MIN_VALUE, 1, 37));
    }

    @Test
    void decimalsReadAsTheirShortestPlainForm() {
        assertEquals(
                "[5] ORDER_REJECTED_RISK_CHECK clientOid=0 listing=0 side=67 price=-0.000000005 size=1000"
                        + " policyId=0\n",
                line(LogMessage.ORDER_REJECTED_RISK_CHECK, 0, 0, 67, -5, 1_000_000_000L, 0));
        assertEquals(
                "[5] ORDER_REJECTED_RISK_CHECK clientOid=0 listing=0 side=BID price=12.3 size=0 policyId=0\n",
                line(LogMessage.ORDER_REJECTED_RISK_CHECK, 0, 0, 'B', 12_300_000_000L, 0, 0));
        assertEquals(
                "[5] ORDER_REJECTED_RISK_CHECK clientOid=0 listing=0 side=BID price=-1.000000001 size=-2.5"
                        + " policyId=0\n",
                line(LogMessage.ORDER_REJECTED_RISK_CHECK, 0, 0, 'B', -1_000_000_001L, -2_500_000, 0));
    }

    @Test
    void aMessageWithoutNamesFallsBackToPositions() {
        assertEquals("[5] DEBUG arg1=7 arg2=8\n", line(LogMessage.DEBUG, 7, 8));
        assertEquals("[5] DEBUG\n", line(LogMessage.DEBUG));
    }

    @Test
    void anUnknownCodePrintsItsNumber() {
        assertEquals(
                "[5] ORDER_REJECTED_HALTED clientOid=1 strategy=7 listing=100 cause=9\n",
                line(LogMessage.ORDER_REJECTED_HALTED, 1, 7, 100, 9));
    }

    @Test
    void textIsWrittenAsItIs() {
        final ExpandableArrayBuffer out = new ExpandableArrayBuffer(64);
        final byte[] text = "venue said: 100% full, ünïcode".getBytes(StandardCharsets.UTF_8);
        final int length = LogFormatter.putTextLine(out, 0, 5, LogMessage.VENUE_RATE_LIMITED, text, text.length);
        assertEquals(
                "[5] VENUE_RATE_LIMITED venue said: 100% full, ünïcode\n",
                new String(out.byteArray(), 0, length, StandardCharsets.UTF_8));
    }

    @Test
    void formattingALineAllocatesNothing() {
        final ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        final ExpandableArrayBuffer out = new ExpandableArrayBuffer(512);
        final long[] values = {814, 100, 'B', 505_000_000L, 10_000_000, 2};
        for (int i = 0; i < 20_000; i++) {
            LogFormatter.putLine(out, 0, i, LogMessage.ORDER_REJECTED_EXCHANGE_CONSTRAINTS, 6, values);
        }
        final long threadId = Thread.currentThread().getId();
        final long baseline = threads.getThreadAllocatedBytes(threadId);
        final long overhead = threads.getThreadAllocatedBytes(threadId) - baseline;
        final long before = threads.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < CALLS; i++) {
            LogFormatter.putLine(out, 0, i, LogMessage.ORDER_REJECTED_EXCHANGE_CONSTRAINTS, 6, values);
        }
        // Under a byte a call: any object a call made would be at least 16. Not exactly zero, since the JIT
        // compiling the loop charges a few hundred bytes to this thread once.
        assertTrue(threads.getThreadAllocatedBytes(threadId) - before - overhead < CALLS);
    }
}

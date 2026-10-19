package group.gnometrading.logging;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Writes log lines as ASCII bytes into a buffer the caller reuses, so a {@link Logger#log} line costs no allocation
 * wherever it is formatted. Each method returns the index just past what it wrote.
 */
final class LogFormatter {

    static final int MAX_ARGS = 6;

    private LogFormatter() {}

    /** {@code [nanos] NAME name=value ...} and a newline. */
    static int putLine(
            final MutableDirectBuffer out,
            final int index,
            final long nanos,
            final LogMessage msg,
            final int count,
            final long[] values) {
        int at = putHeader(out, index, nanos, msg);
        for (int i = 0; i < count; i++) {
            out.putByte(at++, (byte) ' ');
            at = msg.arg(i).append(out, at, values[i]);
        }
        out.putByte(at++, (byte) '\n');
        return at;
    }

    /** {@code [nanos] NAME text} and a newline, for a {@link Logger#logf} line already turned into UTF-8. */
    static int putTextLine(
            final MutableDirectBuffer out,
            final int index,
            final long nanos,
            final LogMessage msg,
            final byte[] text,
            final int length) {
        int at = putHeader(out, index, nanos, msg);
        out.putByte(at++, (byte) ' ');
        out.putBytes(at, text, 0, length);
        at += length;
        out.putByte(at++, (byte) '\n');
        return at;
    }

    /** The same line, with the text read from where the async logger's ring holds it. */
    static int putTextLine(
            final MutableDirectBuffer out,
            final int index,
            final long nanos,
            final LogMessage msg,
            final DirectBuffer text,
            final int textIndex,
            final int length) {
        int at = putHeader(out, index, nanos, msg);
        out.putByte(at++, (byte) ' ');
        out.putBytes(at, text, textIndex, length);
        at += length;
        out.putByte(at++, (byte) '\n');
        return at;
    }

    private static int putHeader(
            final MutableDirectBuffer out, final int index, final long nanos, final LogMessage msg) {
        int at = index;
        out.putByte(at++, (byte) '[');
        at += out.putLongAscii(at, nanos);
        out.putByte(at++, (byte) ']');
        out.putByte(at++, (byte) ' ');
        return at + out.putStringWithoutLengthAscii(at, msg.name());
    }
}

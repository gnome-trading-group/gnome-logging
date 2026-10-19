package group.gnometrading.logging;

import group.gnometrading.utils.FixedPointAscii;
import org.agrona.MutableDirectBuffer;

/**
 * How one argument of a {@link LogMessage} reads in a log line. Callers always pass plain longs, so logging costs
 * them nothing extra; the name and the format are applied only when a line is written.
 */
public final class LogArg {

    // Fixed point as the trading schemas define it: 1e9 per dollar, 1e6 per unit.
    private static final long PRICE_SCALE = 1_000_000_000L;
    private static final long SIZE_SCALE = 1_000_000L;
    // The schemas' null for a 64-bit field, e.g. the price of a market order.
    private static final long NULL_VALUE = Long.MIN_VALUE;

    private enum Kind {
        NUMBER,
        PRICE,
        SIZE,
        SIDE,
        CODE
    }

    private final String name;
    private final Kind kind;
    private final String[] codes;

    private LogArg(final String name, final Kind kind, final String[] codes) {
        this.name = name;
        this.kind = kind;
        this.codes = codes;
    }

    public static LogArg number(final String name) {
        return new LogArg(name, Kind.NUMBER, null);
    }

    /** A fixed-point price or amount of money. */
    public static LogArg price(final String name) {
        return new LogArg(name, Kind.PRICE, null);
    }

    /** A fixed-point quantity. */
    public static LogArg size(final String name) {
        return new LogArg(name, Kind.SIZE, null);
    }

    /** An order side, passed as its schema character ({@code 'B'} or {@code 'A'}). */
    public static LogArg side(final String name) {
        return new LogArg(name, Kind.SIDE, null);
    }

    /** A code printed by name: value {@code i} prints {@code codes[i]}. */
    public static LogArg code(final String name, final String... codes) {
        return new LogArg(name, Kind.CODE, codes);
    }

    public String name() {
        return name;
    }

    /** Writes {@code name=value} at {@code index}, without allocating, and returns the index after it. */
    int append(final MutableDirectBuffer out, final int index, final long value) {
        int at = index + out.putStringWithoutLengthAscii(index, name);
        out.putByte(at++, (byte) '=');
        if (kind != Kind.NUMBER && kind != Kind.CODE && value == NULL_VALUE) {
            return at + out.putStringWithoutLengthAscii(at, "null");
        }
        return switch (kind) {
            case PRICE -> at + FixedPointAscii.put(out, at, value, PRICE_SCALE);
            case SIZE -> at + FixedPointAscii.put(out, at, value, SIZE_SCALE);
            case SIDE -> putSide(out, at, value);
            case CODE -> value >= 0 && value < codes.length
                    ? at + out.putStringWithoutLengthAscii(at, codes[(int) value])
                    : at + out.putLongAscii(at, value);
            default -> at + out.putLongAscii(at, value);
        };
    }

    private static int putSide(final MutableDirectBuffer out, final int index, final long value) {
        if (value == 'B') {
            return index + out.putStringWithoutLengthAscii(index, "BID");
        }
        if (value == 'A') {
            return index + out.putStringWithoutLengthAscii(index, "ASK");
        }
        return index + out.putLongAscii(index, value);
    }
}

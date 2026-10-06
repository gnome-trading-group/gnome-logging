package group.gnometrading.logging;

import java.math.BigDecimal;

/**
 * How one argument of a {@link LogMessage} reads in a log line. Callers always pass plain longs, so logging costs
 * them nothing extra; the name and the format are applied only when a line is written.
 */
public final class LogArg {

    // Fixed point as the trading schemas define it: 1e9 per dollar, 1e6 per unit.
    private static final int PRICE_DECIMALS = 9;
    private static final int SIZE_DECIMALS = 6;
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

    void append(final StringBuilder out, final long value) {
        out.append(name).append('=');
        if (kind != Kind.NUMBER && kind != Kind.CODE && value == NULL_VALUE) {
            out.append("null");
            return;
        }
        switch (kind) {
            case PRICE -> out.append(decimal(value, PRICE_DECIMALS));
            case SIZE -> out.append(decimal(value, SIZE_DECIMALS));
            case SIDE -> out.append(sideName(value));
            case CODE -> out.append(value >= 0 && value < codes.length ? codes[(int) value] : Long.toString(value));
            default -> out.append(value);
        }
    }

    private static String decimal(final long value, final int decimals) {
        return BigDecimal.valueOf(value, decimals).stripTrailingZeros().toPlainString();
    }

    private static String sideName(final long value) {
        if (value == 'B') {
            return "BID";
        }
        if (value == 'A') {
            return "ASK";
        }
        return Long.toString(value);
    }
}

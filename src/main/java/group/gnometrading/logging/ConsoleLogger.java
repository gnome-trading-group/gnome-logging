package group.gnometrading.logging;

import org.agrona.concurrent.EpochNanoClock;

public final class ConsoleLogger implements Logger {

    private static final String DEFAULT_FORMAT = "[%d] %s";

    private final EpochNanoClock clock;

    public ConsoleLogger(EpochNanoClock clock) {
        this.clock = clock;
    }

    @Override
    public void log(LogMessage msg) {
        System.out.printf(DEFAULT_FORMAT, clock.nanoTime(), msg.name());
        System.out.println();
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
        System.out.println(format(clock.nanoTime(), msg, count, v1, v2, v3, v4, v5, v6));
    }

    static String format(long nanos, LogMessage msg, int count, long v1, long v2, long v3, long v4, long v5, long v6) {
        final StringBuilder line =
                new StringBuilder().append('[').append(nanos).append("] ").append(msg.name());
        final long[] values = {v1, v2, v3, v4, v5, v6};
        for (int i = 0; i < count; i++) {
            line.append(' ');
            msg.arg(i).append(line, values[i]);
        }
        return line.toString();
    }

    @Override
    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public void logf(LogMessage msg, String format, Object... args) {
        String formatted = String.format(format, args);
        System.out.printf(DEFAULT_FORMAT + " " + formatted, clock.nanoTime(), msg.name());
        System.out.println();
    }
}

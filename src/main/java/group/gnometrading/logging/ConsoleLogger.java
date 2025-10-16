package group.gnometrading.logging;

import org.agrona.concurrent.EpochNanoClock;

public class ConsoleLogger implements Logger {

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
        String message = DEFAULT_FORMAT + " arg1=%d";
        System.out.printf(message, clock.nanoTime(), msg.name(), v1);
        System.out.println();
    }

    @Override
    public void log(LogMessage msg, long v1, long v2) {
        String message = DEFAULT_FORMAT + " arg1=%d arg2=%d";
        System.out.printf(message, clock.nanoTime(), msg.name(), v1, v2);
        System.out.println();
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3) {
        String message = DEFAULT_FORMAT + " arg1=%d arg2=%d arg3=%d";
        System.out.printf(message, clock.nanoTime(), msg.name(), v1, v2, v3);
        System.out.println();
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3, long v4) {
        String message = DEFAULT_FORMAT + " arg1=%d arg2=%d arg3=%d arg4=%d";
        System.out.printf(message, clock.nanoTime(), msg.name(), v1, v2, v3, v4);
        System.out.println();
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3, long v4, long v5) {
        String message = DEFAULT_FORMAT + " arg1=%d arg2=%d arg3=%d arg4=%d arg5=%d";
        System.out.printf(message, clock.nanoTime(), msg.name(), v1, v2, v3, v4, v5);
        System.out.println();
    }

    @Override
    public void log(LogMessage msg, long v1, long v2, long v3, long v4, long v5, long v6) {
        String message = DEFAULT_FORMAT + " arg1=%d arg2=%d arg3=%d arg4=%d arg5=%d arg6=%d";
        System.out.printf(message, clock.nanoTime(), msg.name(), v1, v2, v3, v4, v5, v6);
        System.out.println();
    }

    @Override
    public void logf(LogMessage msg, String format, Object... args) {
        String formatted = String.format(format, args);
        System.out.printf(DEFAULT_FORMAT + " " + formatted, clock.nanoTime(), msg.name());
        System.out.println();
    }
}

package group.gnometrading.logging;

public interface Logger {
    void log(LogMessage msg);
    void log(LogMessage msg, long v1);
    void log(LogMessage msg, long v1, long v2);
    void log(LogMessage msg, long v1, long v2, long v3);
    void log(LogMessage msg, long v1, long v2, long v3, long v4);
    void log(LogMessage msg, long v1, long v2, long v3, long v4, long v5);
    void log(LogMessage msg, long v1, long v2, long v3, long v4, long v5, long v6);

    /**
     * Log a formatted message. This is not intended for high performance logging.
     *
     * @param msg log message code
     * @param format printf style format string
     * @param args arguments for format string
     */
    void logf(LogMessage msg, String format, Object... args);
}

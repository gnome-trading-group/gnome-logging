package group.gnometrading.logging;

public enum LogMessage {

    /** Socket related messages */
    SOCKET_CONNECTING,
    SOCKET_CONNECTED,

    SOCKET_CONNECT_FAILED,
    SOCKET_CONNECT_TIMED_OUT,

    SOCKET_DISCONNECTING,
    SOCKET_DISCONNECTED,
    SOCKET_SILENCE_TIMED_OUT, // no data received for configured period
    SOCKET_RECONNECTING,

    /** General messages */
    FATAL_ERROR_EXITING,
    UNKNOWN_ERROR,
    DEBUG,

    /** OMS order rejection messages */
    ORDER_REJECTED_EXCHANGE_CONSTRAINTS(
            LogArg.number("clientOid"),
            LogArg.number("listing"),
            LogArg.side("side"),
            LogArg.price("price"),
            LogArg.size("size"),
            LogArg.code("rule", "LOT_SIZE", "MIN_SIZE", "TICK", "MIN_NOTIONAL")),
    /** policyId 0 is a limit with no registry policy: the OMS's own order book being full, or a backtest's limits. */
    ORDER_REJECTED_RISK_CHECK(
            LogArg.number("clientOid"),
            LogArg.number("listing"),
            LogArg.side("side"),
            LogArg.price("price"),
            LogArg.size("size"),
            LogArg.number("policyId")),
    /** Logged when a strategy's orders start being refused as halted, not for every order refused after that. */
    ORDER_REJECTED_HALTED(
            LogArg.number("clientOid"),
            LogArg.number("strategy"),
            LogArg.number("listing"),
            LogArg.code("cause", "STALE_RISK", "KILLED", "LATCHED")),

    /** Trading ledger messages */
    LEDGER_FAILING_HALTED,
    /** status -1 means no response arrived. */
    LEDGER_WRITE_FAILED(LogArg.number("status")),
    LEDGER_FENCED(LogArg.number("status")),

    /** Startup recovery messages; each halts the strategy on the listing until an operator reviews it. */
    VENUE_ORDER_UNATTRIBUTABLE_HALTED(LogArg.number("strategy"), LogArg.number("listing")),
    POSITION_NEEDS_REVIEW_HALTED(LogArg.number("strategy"), LogArg.number("listing")),

    /** OMS execution report messages */
    EXEC_REPORT_FOR_UNKNOWN_ORDER(LogArg.number("clientOid")),
    DUPLICATE_FILL_IGNORED(LogArg.number("clientOid")),
    INVALID_FILL_REPORT(LogArg.number("clientOid"));

    private final LogArg[] args;

    LogMessage(final LogArg... args) {
        this.args = args;
    }

    /** How argument {@code index} reads: its declared name and format, or {@code arg1}, {@code arg2}... if none. */
    LogArg arg(final int index) {
        return index < args.length ? args[index] : LogArg.number("arg" + (index + 1));
    }
}

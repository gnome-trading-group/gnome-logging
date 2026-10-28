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

    /**
     * A market data feed missed or reordered an update: the last book is published flagged maybeBadBook and the reader
     * reconnects for a fresh snapshot. expected and received are the venue's sequence numbers for SEQUENCE and NONCE
     * gaps, and for BEST_PRICE the venue's best price and the book's, on the side that disagreed.
     */
    MARKET_DATA_GAP(
            LogArg.number("listing"),
            LogArg.number("expected"),
            LogArg.number("received"),
            LogArg.code("cause", "SEQUENCE", "NONCE", "BEST_PRICE")),

    /** General messages */
    FATAL_ERROR_EXITING,
    UNKNOWN_ERROR,
    DEBUG,
    /** The async logger's ring was full, so this many lines were dropped rather than make their threads wait. */
    LOG_RECORDS_DROPPED(LogArg.number("count")),

    /** OMS order rejection messages */
    ORDER_REJECTED_EXCHANGE_CONSTRAINTS(
            LogArg.number("clientOid"),
            LogArg.number("listing"),
            LogArg.side("side"),
            LogArg.price("price"),
            LogArg.size("size"),
            LogArg.code("rule", "LOT_SIZE", "MIN_SIZE", "TICK", "MIN_NOTIONAL", "NO_MARKET_PRICE")),
    /** policyId 0 is a limit with no registry policy: the OMS's own order book being full, or a backtest's limits. */
    ORDER_REJECTED_RISK_CHECK(
            LogArg.number("clientOid"),
            LogArg.number("listing"),
            LogArg.side("side"),
            LogArg.price("price"),
            LogArg.size("size"),
            LogArg.number("policyId")),
    /**
     * A market-time policy breached, such as a loss limit or a run of venue rejects: the strategy is halted in the
     * policy's scope and its orders there cancelled. exchange and listing are 0 when the latch covers more than one.
     */
    STRATEGY_LATCHED(
            LogArg.number("strategy"),
            LogArg.number("exchange"),
            LogArg.number("listing"),
            LogArg.code("cause", "MARKET_LIMIT", "REJECT_STREAK"),
            LogArg.number("policyId")),
    /** Logged when a strategy's orders start being refused as halted, not for every order refused after that. */
    ORDER_REJECTED_HALTED(
            LogArg.number("clientOid"),
            LogArg.number("strategy"),
            LogArg.number("listing"),
            LogArg.code("cause", "STALE_RISK", "KILLED", "LATCHED", "UNGUARDED")),
    /**
     * A listing's book is flagged bad data, so the OMS refuses new orders on it until a good book arrives. Logged when
     * a strategy's orders on the listing start being refused, not for every order refused after that.
     */
    ORDER_REJECTED_MARKET_DATA_BAD(LogArg.number("clientOid"), LogArg.number("strategy"), LogArg.number("listing")),
    /**
     * A live session lacks a required risk policy on a listing, so nothing may trade until one is added. Logged once
     * per policy change, naming the first gap found.
     */
    RISK_UNGUARDED(
            LogArg.number("strategy"),
            LogArg.number("listing"),
            LogArg.code(
                    "missing",
                    "MAX_COMMITTED_CAPITAL",
                    "MAX_TOTAL_PNL_LOSS",
                    "MAX_POSITION",
                    "MAX_CONSECUTIVE_REJECTS")),

    /** A venue refused an order, cancel or amend; logged with logf, as the venue's reason is its own text. */
    ORDER_REJECTED_BY_VENUE,

    /**
     * Rate limiting of a venue account's requests, each logged with logf as the venue and bucket are text, and once
     * as each starts or ends rather than for every request. This one: the venue answered 429, so requests are held
     * for its penalty and this process's budget is lowered.
     */
    VENUE_RATE_LIMITED,
    /** The account's budget ran out: new orders and amends are being refused locally, without being sent. */
    RATE_LIMIT_THROTTLING,
    /** Requests are being sent again; logged with how many were refused meanwhile. */
    RATE_LIMIT_RECOVERED,

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

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
}

package group.gnometrading.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConsoleLoggerTest {

    @Test
    void writesEachLineAsItIsLogged() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ConsoleLogger logger = new ConsoleLogger(() -> 5, new PrintStream(bytes, true, StandardCharsets.UTF_8));

        logger.log(LogMessage.SOCKET_CONNECTED);
        logger.log(LogMessage.LEDGER_WRITE_FAILED, 503);

        assertEquals(
                "[5] SOCKET_CONNECTED\n[5] LEDGER_WRITE_FAILED status=503\n", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void formattedMessageIsPrintedAsItIsEvenWithPercentSigns() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ConsoleLogger logger = new ConsoleLogger(() -> 5, new PrintStream(bytes, true, StandardCharsets.UTF_8));

        logger.logf(LogMessage.VENUE_RATE_LIMITED, "budget %.0f%% of the rate; venue said: %s", 40.0, "100% full");

        assertEquals(
                "[5] VENUE_RATE_LIMITED budget 40% of the rate; venue said: 100% full\n",
                bytes.toString(StandardCharsets.UTF_8));
    }
}

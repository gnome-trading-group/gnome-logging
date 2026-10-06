package group.gnometrading.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConsoleLoggerTest {

    @Test
    void namesEachArgumentAndPrintsPricesSizesSidesAndCodesReadably() {
        final String line = ConsoleLogger.format(
                5, LogMessage.ORDER_REJECTED_EXCHANGE_CONSTRAINTS, 6, 814, 100, 'B', 505_000_000L, 10_000_000, 2);
        assertEquals(
                "[5] ORDER_REJECTED_EXCHANGE_CONSTRAINTS clientOid=814 listing=100 side=BID price=0.505 size=10"
                        + " rule=TICK",
                line);
    }

    @Test
    void aMarketOrdersMissingPriceReadsAsNull() {
        final String line =
                ConsoleLogger.format(5, LogMessage.ORDER_REJECTED_RISK_CHECK, 6, 1, 100, 'A', Long.MIN_VALUE, 1, 37);
        assertEquals(
                "[5] ORDER_REJECTED_RISK_CHECK clientOid=1 listing=100 side=ASK price=null size=0.000001"
                        + " policyId=37",
                line);
    }

    @Test
    void aMessageWithoutNamesFallsBackToPositions() {
        assertEquals("[5] DEBUG arg1=7 arg2=8", ConsoleLogger.format(5, LogMessage.DEBUG, 2, 7, 8, 0, 0, 0, 0));
    }

    @Test
    void anUnknownCodePrintsItsNumber() {
        assertEquals(
                "[5] ORDER_REJECTED_HALTED clientOid=1 strategy=7 listing=100 cause=9",
                ConsoleLogger.format(5, LogMessage.ORDER_REJECTED_HALTED, 4, 1, 7, 100, 9, 0, 0));
    }
}

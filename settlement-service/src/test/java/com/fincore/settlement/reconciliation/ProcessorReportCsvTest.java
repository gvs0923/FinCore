package com.fincore.settlement.reconciliation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessorReportCsvTest {

    private static final String HEADER = "processorRef,currency,amountMinor,feeMinor\n";

    @Test
    void parsesRowsWithBomCrlfAndQuotedFields() {
        var rows = ProcessorReportCsv.parse("\uFEFF" + HEADER.replace("\n", "\r\n")
                + "\"ref,1\",USD,10000,320\r\nrefund:abc,USD,-500,0");

        assertEquals(2, rows.size());
        assertEquals("ref,1", rows.get(0).processorRef());
        assertEquals(10_000, rows.get(0).amountMinor());
        assertEquals(-500, rows.get(1).amountMinor());
    }

    @Test
    void rejectsInvalidReports() {
        for (String csv : new String[]{
                "",
                "wrong\nx,USD,1,0",
                HEADER,
                HEADER + "x,USD,1",
                HEADER + "x,USD,1.5,0",
                HEADER + "x,USD,0,0",
                HEADER + "x,USD,10,-1",
                HEADER + "x,usd,10,1",
                HEADER + "x,USD,10,1\nx,USD,20,1",
                HEADER + "\"x,USD,10,1",
        }) {
            assertThrows(IllegalArgumentException.class, () -> ProcessorReportCsv.parse(csv), csv);
        }
    }
}

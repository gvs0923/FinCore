package com.fincore.settlement.reconciliation;

import com.fincore.settlement.reconciliation.ReconciliationModels.SettlementRow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Same format and rules as the admin UI's processorCsv.ts.
public final class ProcessorReportCsv {

    public static final String HEADER = "processorRef,currency,amountMinor,feeMinor";
    public static final int MAX_CHARS = 2_000_000;
    private static final int MAX_ROWS = 10_000;

    private ProcessorReportCsv() {
    }

    public static List<SettlementRow> parse(String csv) {
        if (csv.length() > MAX_CHARS) throw new IllegalArgumentException("Report exceeds 2 MB");
        List<List<String>> records = records(csv.replaceFirst("^\uFEFF", "").replace("\r\n", "\n"));
        if (records.isEmpty() || !String.join(",", records.removeFirst()).equals(HEADER)) {
            throw new IllegalArgumentException("CSV header must be " + HEADER);
        }
        if (records.isEmpty() || records.size() > MAX_ROWS) {
            throw new IllegalArgumentException("Report must contain 1-10,000 rows");
        }
        Set<String> refs = new HashSet<>();
        List<SettlementRow> rows = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            List<String> r = records.get(i);
            int line = i + 2;
            if (r.size() != 4) throw new IllegalArgumentException("Invalid column count on row " + line);
            SettlementRow row;
            try {
                if (r.get(0).length() > 255 || !r.get(2).matches("-?\\d+") || !r.get(3).matches("\\d+")) {
                    throw new IllegalArgumentException();
                }
                row = new SettlementRow(r.get(0), r.get(1), Long.parseLong(r.get(2)), Long.parseLong(r.get(3)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid values on row " + line);
            }
            if (!refs.add(row.processorRef())) {
                throw new IllegalArgumentException("Duplicate reference on row " + line);
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<List<String>> records(String input) {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < input.length() && input.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                    closed = true;
                } else {
                    field.append(c);
                }
            } else if (c == ',' || c == '\n') {
                fields.add(field.toString());
                field.setLength(0);
                closed = false;
                if (c == '\n') {
                    records.add(fields);
                    fields = new ArrayList<>();
                }
            } else if (c == '"' && field.isEmpty() && !closed) {
                quoted = true;
            } else {
                if (closed || c == '"' || c == '\r') throw new IllegalArgumentException("Malformed CSV");
                field.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed CSV quote");
        if (!field.isEmpty() || !fields.isEmpty() || closed) {
            fields.add(field.toString());
            records.add(fields);
        }
        return records;
    }
}

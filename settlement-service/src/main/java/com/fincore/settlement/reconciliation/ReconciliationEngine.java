package com.fincore.settlement.reconciliation;

import com.fincore.settlement.reconciliation.ReconciliationModels.Classification;
import com.fincore.settlement.reconciliation.ReconciliationModels.Comparison;
import com.fincore.settlement.reconciliation.ReconciliationModels.SettlementRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

@Component
public class ReconciliationEngine {

    public List<Comparison> compare(
            List<SettlementRow> internalRows,
            List<SettlementRow> externalRows) {
        Map<String, SettlementRow> internal = index(internalRows);
        Map<String, SettlementRow> external = index(externalRows);

        TreeSet<String> references = new TreeSet<>(internal.keySet());
        references.addAll(external.keySet());

        List<Comparison> comparisons = new ArrayList<>();

        for (String reference : references) {
            SettlementRow internalRow = internal.get(reference);
            SettlementRow externalRow = external.get(reference);

            Classification classification;

            if (internalRow == null) {
                classification = Classification.MISSING_INTERNAL;
            } else if (externalRow == null) {
                classification = Classification.MISSING_EXTERNAL;
            } else if (internalRow.equals(externalRow)) {
                classification = Classification.MATCHED;
            } else {
                classification = Classification.MISMATCHED;
            }

            comparisons.add(new Comparison(
                    reference, classification, internalRow, externalRow));
        }

        return List.copyOf(comparisons);
    }

    private static Map<String, SettlementRow> index(
            List<SettlementRow> rows) {
        Map<String, SettlementRow> indexed = new TreeMap<>();

        for (SettlementRow row : rows) {
            if (row == null) {
                throw new IllegalArgumentException(
                        "Settlement rows cannot be null");
            }
            if (indexed.putIfAbsent(row.processorRef(), row) != null) {
                throw new IllegalArgumentException(
                        "Duplicate processor reference: "
                                + row.processorRef());
            }
        }
        return indexed;
    }
}

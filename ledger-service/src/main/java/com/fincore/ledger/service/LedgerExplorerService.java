package com.fincore.ledger.service;

import com.fincore.ledger.api.ExplorerViews.AccountRow;
import com.fincore.ledger.api.ExplorerViews.EntryRow;
import com.fincore.ledger.api.ExplorerViews.JournalView;
import com.fincore.ledger.domain.LedgerEntry;
import com.fincore.ledger.domain.LedgerTransaction;
import com.fincore.ledger.repository.LedgerEntryRepository;
import com.fincore.ledger.repository.LedgerTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class LedgerExplorerService {

    private final JdbcTemplate jdbc;
    private final LedgerEntryRepository entryRepository;
    private final LedgerTransactionRepository transactionRepository;

    public LedgerExplorerService(
            JdbcTemplate jdbc,
            LedgerEntryRepository entryRepository,
            LedgerTransactionRepository transactionRepository) {
        this.jdbc = jdbc;
        this.entryRepository = entryRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountRow> accounts() {
        // Plain JOIN query — the balance table is a projection meant
        // for exactly this read. No locking annotation, no entity.
        return jdbc.query("""
                SELECT a.account_ref, a.account_type, a.currency,
                       b.balance
                FROM accounts a
                JOIN account_balances b ON b.account_id = a.id
                ORDER BY a.account_ref
                """,
                (rs, rowNum) -> new AccountRow(
                        rs.getString("account_ref"),
                        rs.getString("account_type"),
                        rs.getString("currency"),
                        rs.getBigDecimal("balance")));
    }

   @Transactional(readOnly = true)
    public List<EntryRow> entriesForAccount(String accountRef) {
        return jdbc.query("""
                SELECT e.id, e.transaction_id, t.reference,
                       e.entry_type, e.amount, e.currency, e.created_at
                FROM ledger_entries e
                JOIN accounts a ON a.id = e.account_id
                JOIN ledger_transactions t ON t.id = e.transaction_id
                WHERE a.account_ref = ?
                ORDER BY e.created_at DESC
                LIMIT 200
                """,
                (rs, rowNum) -> new EntryRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("transaction_id", UUID.class),
                        rs.getString("reference"),
                        rs.getString("entry_type"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getObject("created_at",
                                java.time.OffsetDateTime.class)),
                accountRef);
    }

        @Transactional(readOnly = true)
    public JournalView journal(String reference) {
        LedgerTransaction tx = transactionRepository
                .findByReference(reference)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No transaction for reference " + reference));

        List<LedgerEntry> entries =
                entryRepository.findByTransactionId(tx.getId());

        List<EntryRow> rows = entries.stream()
                .map(e -> new EntryRow(
                        e.getId(),
                        tx.getId(),
                        reference,
                        e.getEntryType().name(),
                        e.getAmount(),
                        e.getCurrency(),
                        e.getCreatedAt()))
                .toList();

        return new JournalView(
                tx.getId(), reference, tx.getDescription(),
                tx.getCreatedAt(), rows);
    }
}
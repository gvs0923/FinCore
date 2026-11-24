package com.fincore.ledger.service;

import com.fincore.ledger.domain.Account;
import com.fincore.ledger.domain.AccountBalance;
import com.fincore.ledger.domain.EntryType;
import com.fincore.ledger.domain.LedgerEntry;
import com.fincore.ledger.domain.LedgerTransaction;
import com.fincore.ledger.outbox.OutboxTrace;
import com.fincore.ledger.repository.AccountBalanceRepository;
import com.fincore.ledger.repository.AccountRepository;
import com.fincore.ledger.repository.LedgerEntryRepository;
import com.fincore.ledger.repository.LedgerTransactionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ledger is FinCore's source of financial truth. Every financial
 * event is posted here as a balanced set of journal entries; nothing
 * about account state is ever mutated destructively — only appended to.
 */
@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final AccountBalanceRepository balanceRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final OutboxTrace outboxTrace;

    public LedgerService(AccountRepository accountRepository,
                          LedgerTransactionRepository transactionRepository,
                          LedgerEntryRepository entryRepository,
                          AccountBalanceRepository balanceRepository,
                          JdbcTemplate jdbc,
                          ObjectMapper objectMapper,
                          OutboxTrace outboxTrace) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.balanceRepository = balanceRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.outboxTrace = outboxTrace;
    }

    /**
     * Posts a balanced transaction: validates that debits equal credits,
     * writes the immutable journal entries, and updates the read-optimized
     * balance projection for each affected account — all atomically.
     *
     * @param reference   a unique business reference (e.g. payment id).
     *                     Re-posting the same reference is rejected, which
     *                     is what makes ledger posting safe to retry.
     * @param description human-readable description of the transaction.
     * @param lines       the journal lines. Must contain at least one debit
     *                     and one credit, and total debits must equal total
     *                     credits.
     */
    @Transactional
    public LedgerTransaction postTransaction(String reference, String description, List<JournalLine> lines) {
        assertBalanced(lines);

        transactionRepository.findByReference(reference).ifPresent(existing -> {
            throw new DuplicatePostingException(reference);
        });

        LedgerTransaction transaction = transactionRepository.save(new LedgerTransaction(reference, description));

        // Lock accounts in a deterministic order (by accountRef) to avoid
        // deadlocks when concurrent transactions touch overlapping accounts.
        List<JournalLine> orderedLines = lines.stream()
                .sorted(Comparator.comparing(JournalLine::accountRef))
                .toList();

        List<LedgerEntry> savedEntries = new ArrayList<>();
        for (JournalLine line : orderedLines) {
            Account account = accountRepository.findByAccountRef(line.accountRef())
                    .orElseThrow(() -> new AccountNotFoundException(line.accountRef()));

            LedgerEntry entry = entryRepository.save(
                    new LedgerEntry(transaction, account, line.entryType(), line.amount(), account.getCurrency()));
            savedEntries.add(entry);

            // ADR-001: balance row MUST exist — it was created when the account
            // was created. If it's missing, that's a data integrity violation,
            // not something we paper over with lazy creation.
            AccountBalance balance = balanceRepository.findByAccountId(account.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Balance row missing for account " + account.getAccountRef()
                                    + " — ADR-001 violated. Was the account created via AccountService?"));
            balance.apply(line.entryType(), line.amount(), account.getAccountType());
            balanceRepository.save(balance);
        }

        // After saving all entries and updating balances, write to outbox
        jdbc.update("""
                INSERT INTO ledger_outbox_events
                    (aggregate_type, aggregate_id, event_type, payload, traceparent)
                VALUES ('transaction', ?, 'ledger.transaction.posted', ?::jsonb, ?)
                """,
                transaction.getId().toString(),
                buildTransactionPayload(transaction, savedEntries),
                outboxTrace.currentTraceparent());

        return transaction;
    }

    private static void assertBalanced(List<JournalLine> lines) {
        if (lines.size() < 2) {
            throw new IllegalArgumentException("A transaction requires at least one debit and one credit line");
        }
        BigDecimal totalDebits = sum(lines, EntryType.DEBIT);
        BigDecimal totalCredits = sum(lines, EntryType.CREDIT);
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new UnbalancedTransactionException(totalDebits, totalCredits);
        }
    }

    private static BigDecimal sum(List<JournalLine> lines, EntryType type) {
        return lines.stream()
                .filter(line -> line.entryType() == type)
                .map(JournalLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String buildTransactionPayload(LedgerTransaction transaction, List<LedgerEntry> entries) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transaction.getId().toString());
        payload.put("reference", transaction.getReference());
        payload.put("description", transaction.getDescription());

        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        payload.put("entryCount", entries.size());
        payload.put("totalDebits", totalDebits);
        payload.put("totalCredits", totalCredits);

        List<Map<String, Object>> entryList = entries.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entryId", e.getId().toString());
            m.put("accountRef", e.getAccount().getAccountRef());
            m.put("entryType", e.getEntryType().name());
            m.put("amount", e.getAmount().toPlainString());
            m.put("currency", e.getCurrency());
            return m;
        }).toList();
        payload.put("entries", entryList);

        return objectMapper.writeValueAsString(payload);
    }
}

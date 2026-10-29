package com.fincore.ledger.service;

import com.fincore.ledger.TestcontainersConfiguration;
import com.fincore.ledger.domain.Account;
import com.fincore.ledger.domain.AccountBalance;
import com.fincore.ledger.domain.AccountType;
import com.fincore.ledger.repository.AccountBalanceRepository;
import com.fincore.ledger.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3.5 verification: the integrity check must go red when a
 * hand-crafted drift row lands in account_balances, and green again
 * after rebuildAll() replays the journal.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BalanceProjectionIT {

    @Autowired private LedgerService ledgerService;
    @Autowired private BalanceProjectionService projectionService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountBalanceRepository accountBalanceRepository;

    private Account wallet;
    private Account receivable;

    @BeforeEach
    void setUp() {
        wallet = accountRepository.save(new Account(
                "TEST:WALLET:" + System.nanoTime(), AccountType.LIABILITY, "USD"));
        receivable = accountRepository.save(new Account(
                "TEST:RECEIVABLE:" + System.nanoTime(), AccountType.ASSET, "USD"));
        accountBalanceRepository.save(new AccountBalance(wallet.getId()));
        accountBalanceRepository.save(new AccountBalance(receivable.getId()));
    }

    @Test
    void injectedDrift_isDetected_thenClearedByRebuild() {
        // Post a real balanced transaction so the journal has truth to replay
        ledgerService.postTransaction(
                "TX-" + System.nanoTime(),
                "setup posting",
                List.of(
                        JournalLine.debit(receivable.getAccountRef(), new BigDecimal("250.00")),
                        JournalLine.credit(wallet.getAccountRef(), new BigDecimal("250.00"))));

        // Inject drift: overwrite the stored projection with a wrong number
        AccountBalance drifted = accountBalanceRepository.findById(receivable.getId())
                .orElseThrow();
        drifted.overwriteBalance(new BigDecimal("999.99"));
        accountBalanceRepository.save(drifted);

        // RED: integrity check must catch it
        var results = projectionService.checkIntegrity();
        var row = results.stream()
                .filter(r -> r.accountId().equals(receivable.getId()))
                .findFirst().orElseThrow();
        assertThat(row.drifted()).isTrue();
        assertThat(row.stored()).isEqualByComparingTo("999.99");
        assertThat(row.recomputed()).isEqualByComparingTo("250.00");

        // GREEN: replay the journal, drift is gone
        projectionService.rebuildAll();

        var after = projectionService.checkIntegrity();
        var fixed = after.stream()
                .filter(r -> r.accountId().equals(receivable.getId()))
                .findFirst().orElseThrow();
        assertThat(fixed.drifted()).isFalse();
        assertThat(fixed.stored()).isEqualByComparingTo("250.00");
    }
}

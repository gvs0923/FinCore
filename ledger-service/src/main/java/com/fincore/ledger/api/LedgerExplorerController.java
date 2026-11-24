package com.fincore.ledger.api;

import com.fincore.ledger.api.ExplorerViews.AccountRow;
import com.fincore.ledger.api.ExplorerViews.EntryRow;
import com.fincore.ledger.api.ExplorerViews.JournalView;
import com.fincore.ledger.service.LedgerExplorerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/ledger")
public class LedgerExplorerController {

    private final LedgerExplorerService explorerService;

    public LedgerExplorerController(
            LedgerExplorerService explorerService) {
        this.explorerService = explorerService;
    }

    @GetMapping("/accounts")
    public List<AccountRow> accounts() {
        return explorerService.accounts();
    }

    @GetMapping("/accounts/{accountRef}/entries")
    public List<EntryRow> accountEntries(
            @PathVariable String accountRef) {
        return explorerService.entriesForAccount(accountRef);
    }

    @GetMapping("/transactions/{reference:.+}")
    public JournalView journal(@PathVariable String reference) {
        // {reference:.+} lets refs like "payment:uuid" match —
        // Spring's default path-variable regex trims at dots.
        return explorerService.journal(reference);
    }
}
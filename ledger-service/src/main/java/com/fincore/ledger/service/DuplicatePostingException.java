package com.fincore.ledger.service;

public class DuplicatePostingException extends IllegalStateException {
    public DuplicatePostingException(String reference) {
        super("Ledger transaction already posted for reference: " + reference);
    }
}

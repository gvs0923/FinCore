INSERT INTO accounts (account_ref, account_type, currency)
VALUES
    ('REVENUE:PROCESSOR_FEES', 'REVENUE', 'USD'),
    ('LIABILITY:MERCHANT_PAYOUT', 'LIABILITY', 'USD')
ON CONFLICT (account_ref) DO NOTHING;

INSERT INTO account_balances (account_id, balance)
SELECT id, 0 FROM accounts
WHERE account_ref IN (
    'REVENUE:PROCESSOR_FEES',
    'LIABILITY:MERCHANT_PAYOUT'
)
ON CONFLICT (account_id) DO NOTHING;

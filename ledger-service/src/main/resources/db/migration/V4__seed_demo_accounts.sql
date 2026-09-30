INSERT INTO accounts (account_ref, account_type, currency)
VALUES
    ('ASSET:PROCESSOR_CLEARING',    'ASSET',     'USD'),
    ('LIABILITY:MERCHANT_RECEIVABLE','LIABILITY', 'USD'),
    ('ASSET:BANK',                   'ASSET',     'USD'),
    ('REVENUE:FEES',                 'REVENUE',   'USD')
ON CONFLICT (account_ref) DO NOTHING;

-- Provision zero-balance rows (ADR-001: eager, never lazy).
INSERT INTO account_balances (account_id)
SELECT id FROM accounts
WHERE account_ref IN (
    'ASSET:PROCESSOR_CLEARING',
    'LIABILITY:MERCHANT_RECEIVABLE',
    'ASSET:BANK',
    'REVENUE:FEES'
)
ON CONFLICT (account_id) DO NOTHING;
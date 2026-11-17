// src/admin/api/ledgerMoney.ts
import { formatMoney } from "../../lib/money";

/** Ledger NUMERIC → minor units for display. Read-only formatting. */
export function ledgerAmountToMinor(decimal: string | number): number {
  return Math.round(Number(decimal) * 100);
}

export function formatLedgerAmount(
    decimal: string | number, currency: string): string {
  return formatMoney(ledgerAmountToMinor(decimal), currency);
}
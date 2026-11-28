import json
import re

from pydantic import Field

from app.llm import LlmClient, LlmUnavailable
from app.models import CamelModel

_NUMBER = re.compile(r"-?\d[\d,]*")

SYSTEM = """You summarise payment reconciliation breaks for a finance operations team.
Use ONLY the AGGREGATES JSON. Amounts are integer minor units: write them as '<n> minor units <CUR>'.
Quote numbers exactly as given. Never compute new numbers, percentages or totals.
You may say which pattern the counts suggest (e.g. fee-only mismatches suggest a fee-rate difference),
but label it as a hypothesis. Do not recommend ledger postings. At most 4 sentences, plain text."""


class ReconDelta(CamelModel):
    processor_ref: str
    amount_delta_minor: int
    fee_delta_minor: int


class ReconAggregates(CamelModel):
    run_id: str
    batch_id: str
    report_ref: str
    currency: str
    item_count: int
    matched: int
    mismatched: int
    missing_internal: int
    missing_external: int
    amount_only_mismatches: int
    fee_only_mismatches: int
    amount_and_fee_mismatches: int
    currency_mismatches: int
    net_amount_delta_minor: int
    net_fee_delta_minor: int
    missing_internal_amount_minor: int
    missing_external_amount_minor: int
    largest_deltas: list[ReconDelta] = Field(max_length=5)


class AnomalySummary(CamelModel):
    summary: str
    generator: str
    fallback_reason: str | None = None


def _allowed_numbers(a: ReconAggregates) -> set[int]:
    allowed = {0, len(a.largest_deltas)}

    def collect(value) -> None:
        if isinstance(value, bool):
            return
        if isinstance(value, int):
            allowed.add(abs(value))
        elif isinstance(value, str):
            allowed.update(abs(int(n.replace(",", ""))) for n in _NUMBER.findall(value))
        elif isinstance(value, dict):
            for v in value.values():
                collect(v)
        elif isinstance(value, list):
            for v in value:
                collect(v)

    collect(a.model_dump())
    return allowed


def ungrounded_numbers(text: str, a: ReconAggregates) -> set[int]:
    allowed = _allowed_numbers(a)
    return {n for n in (abs(int(m.replace(",", ""))) for m in _NUMBER.findall(text)) if n not in allowed}


def template(a: ReconAggregates, reason: str | None) -> AnomalySummary:
    cur = a.currency
    parts = [f"Report {a.report_ref}: {a.item_count} rows, {a.matched} matched, {a.mismatched} mismatched "
             f"({a.amount_only_mismatches} amount only, {a.fee_only_mismatches} fee only, "
             f"{a.amount_and_fee_mismatches} both, {a.currency_mismatches} currency), "
             f"{a.missing_internal} missing internally, {a.missing_external} missing from the processor report."]
    if a.mismatched:
        parts.append(f"Net mismatch delta (processor minus internal, same-currency rows): amount "
                     f"{a.net_amount_delta_minor} minor units {cur}, fees {a.net_fee_delta_minor} minor units {cur}.")
    if a.missing_internal:
        parts.append(f"Rows missing internally total {a.missing_internal_amount_minor} minor units {cur}.")
    if a.missing_external:
        parts.append(f"Rows missing from the report total {a.missing_external_amount_minor} minor units {cur}.")
    if a.largest_deltas:
        parts.append("Largest deltas (amount/fee): " + ", ".join(
            f"{d.processor_ref} ({d.amount_delta_minor:+d}/{d.fee_delta_minor:+d})" for d in a.largest_deltas) + ".")
    return AnomalySummary(summary=" ".join(parts), generator="template", fallback_reason=reason)


class ReconSummarizer:
    def __init__(self, llm: LlmClient):
        self._llm = llm

    async def summarize(self, a: ReconAggregates) -> AnomalySummary:
        if a.item_count == a.matched:
            return AnomalySummary(summary=f"No breaks: all {a.item_count} rows matched.", generator="template")
        try:
            text = (await self._llm.complete(
                [{"role": "system", "content": SYSTEM},
                 {"role": "user", "content": "AGGREGATES:\n" + json.dumps(a.model_dump(by_alias=True))}],
                max_tokens=300)).strip()
        except LlmUnavailable as e:
            return template(a, f"llm_{e}" if self._llm.enabled else "llm_not_configured")
        if not text or len(text) > 1500:
            return template(a, "rejected:bad_length")
        if bad := ungrounded_numbers(text, a):
            return template(a, f"rejected:ungrounded_numbers:{sorted(bad)[:3]}")
        return AnomalySummary(summary=text, generator=self._llm.name)
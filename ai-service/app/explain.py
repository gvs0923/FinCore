import hashlib
import json
import re
from collections import OrderedDict
from typing import Literal

from pydantic import Field

from app.llm import LlmClient, LlmUnavailable
from app.models import CamelModel
from app.rules import BLOCK_AT, ML_REVIEW_AT, REVIEW_AT, RULES

_DECISIONS = ("ALLOW", "REVIEW", "BLOCK")

SYSTEM = """You explain payment risk decisions to fraud analysts.
Use ONLY the FACTS JSON. Do not guess the customer's intent, identity or location.
Mention every fired rule by its exact id in backticks, and no other rule id.
State the decision word exactly as given. Do not recommend actions.
Reply with JSON only: {"summary": "<at most 3 sentences>", "citedRules": ["<rule id>", ...]}"""


class RiskExplainRequest(CamelModel):
    decision: Literal["ALLOW", "REVIEW", "BLOCK"]
    score: int
    fired_rules: list[str] = Field(max_length=20)
    signals: dict[str, bool | int | float | str | None] = Field(max_length=30)
    ml_probability: float | None = Field(default=None, ge=0, le=1)
    ml_model_version: str | None = None


class RiskExplanation(CamelModel):
    summary: str
    cited_rules: list[str]
    generator: str
    fallback_reason: str | None = None


def template(req: RiskExplainRequest, reason: str | None) -> RiskExplanation:
    parts = [f"Decision {req.decision} with rule score {req.score} (review at {REVIEW_AT}, block at {BLOCK_AT})."]
    parts.append("Fired: " + "; ".join(f"`{r}` ({RULES.get(r, 'unrecognised rule')})" for r in req.fired_rules) + "."
                 if req.fired_rules else "No rules fired.")
    parts.append("ML score unavailable; the decision used rules only." if req.ml_probability is None
                 else f"ML probability {req.ml_probability:.2f} from model {req.ml_model_version}.")
    return RiskExplanation(summary=" ".join(parts), cited_rules=list(req.fired_rules),
                           generator="template", fallback_reason=reason)


def _validate(req: RiskExplainRequest, raw: str, generator: str) -> RiskExplanation:
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        raise ValueError("not_json") from e
    summary, cited = data.get("summary"), data.get("citedRules")
    if not isinstance(summary, str) or not summary.strip() or len(summary) > 1200:
        raise ValueError("bad_summary")
    if not isinstance(cited, list) or not all(isinstance(c, str) for c in cited):
        raise ValueError("bad_citations")
    fired = set(req.fired_rules)
    if set(cited) != fired:
        raise ValueError("citations_do_not_match_fired_rules")
    if {rid for rid in RULES if re.search(rf"\b{re.escape(rid)}\b", summary)} - fired:
        raise ValueError("mentions_unfired_rule")
    if req.decision not in summary or any(d in summary for d in _DECISIONS if d != req.decision):
        raise ValueError("wrong_or_missing_decision")
    return RiskExplanation(summary=summary.strip(), cited_rules=sorted(fired), generator=generator)


class RiskExplainer:
    def __init__(self, llm: LlmClient, cache_size: int = 512):
        self._llm = llm
        self._cache: OrderedDict[str, RiskExplanation] = OrderedDict()
        self._cache_size = cache_size

    async def explain(self, req: RiskExplainRequest) -> RiskExplanation:
        key = hashlib.sha256(req.model_dump_json().encode()).hexdigest()
        if key in self._cache:
            self._cache.move_to_end(key)
            return self._cache[key]
        facts = {
            "decision": req.decision, "ruleScore": req.score,
            "reviewThreshold": REVIEW_AT, "blockThreshold": BLOCK_AT,
            "firedRules": [{"id": r, "meaning": RULES.get(r, "unrecognised rule")} for r in req.fired_rules],
            "signals": req.signals,
            "ml": "unavailable" if req.ml_probability is None
            else {"probability": req.ml_probability, "model": req.ml_model_version},
            "mlPolicy": f"ML can only escalate ALLOW to REVIEW at probability >= {ML_REVIEW_AT}",
        }
        try:
            raw = await self._llm.complete(
                [{"role": "system", "content": SYSTEM},
                 {"role": "user", "content": "FACTS:\n" + json.dumps(facts)}],
                json_mode=True, max_tokens=300)
            result = _validate(req, raw, self._llm.name)
        except LlmUnavailable as e:
            return template(req, f"llm_{e}" if self._llm.enabled else "llm_not_configured")
        except ValueError as e:
            return template(req, f"rejected:{e}")
        self._cache[key] = result
        if len(self._cache) > self._cache_size:
            self._cache.popitem(last=False)
        return result
import json
import math
from pathlib import Path

from pydantic import Field

from app.models import CamelModel

FEATURES = ("log_amount", "card_hits", "ip_hits", "new_device",
            "country_changed", "timezone_changed", "language_changed")
MODEL_PATH = Path(__file__).with_name("risk_model_v1.json")
_MAX_HITS = 50


class ScoreRequest(CamelModel):
    amount_minor: int = Field(ge=0)
    card_hits: int = Field(ge=0)
    ip_hits: int = Field(ge=0)
    new_device: bool
    country_changed: bool
    timezone_changed: bool
    language_changed: bool


class ScoreResponse(CamelModel):
    probability: float
    model_version: str
    contributions: dict[str, float]


def feature_vector(r: ScoreRequest) -> list[float]:
    """The ONLY feature code. Training imports it too, so train and serve cannot drift."""
    return [math.log1p(r.amount_minor), min(r.card_hits, _MAX_HITS), min(r.ip_hits, _MAX_HITS),
            float(r.new_device), float(r.country_changed), float(r.timezone_changed),
            float(r.language_changed)]


class LogisticModel:
    def __init__(self, path: Path = MODEL_PATH):
        spec = json.loads(path.read_text())
        if tuple(spec["features"]) != FEATURES:
            raise RuntimeError(f"{path.name}: features {spec['features']} != serving order {FEATURES}")
        if not all(len(spec[k]) == len(FEATURES) for k in ("weights", "means", "scales")):
            raise RuntimeError(f"{path.name}: weights/means/scales length mismatch")
        self.version = spec["version"]
        self._bias = float(spec["bias"])
        self._weights = [float(w) for w in spec["weights"]]
        self._means = [float(m) for m in spec["means"]]
        self._scales = [float(s) or 1.0 for s in spec["scales"]]

    def score(self, r: ScoreRequest) -> ScoreResponse:
        x = feature_vector(r)
        contrib = [w * (v - m) / s for w, v, m, s in zip(self._weights, x, self._means, self._scales)]
        z = max(min(self._bias + sum(contrib), 30.0), -30.0)
        return ScoreResponse(probability=round(1.0 / (1.0 + math.exp(-z)), 4), model_version=self.version,
                             contributions={n: round(c, 3) for n, c in zip(FEATURES, contrib)})
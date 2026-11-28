"""Fit the demo risk model on SYNTHETIC data and overwrite risk_model_v1.json.

    pip install -r requirements-train.txt && python -m app.scoring.train

Synthetic labels prove the plumbing, not fraud-detection quality.
"""
import json
import random
from datetime import datetime, timezone

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

from app.scoring.model import FEATURES, MODEL_PATH, ScoreRequest, feature_vector

SEED, ROWS, FRAUD_RATE = 7, 20_000, 0.05


def synthetic(rng: random.Random) -> tuple[np.ndarray, np.ndarray]:
    rows, labels = [], []
    for _ in range(ROWS):
        fraud = rng.random() < FRAUD_RATE
        r = ScoreRequest(
            amount_minor=int(rng.lognormvariate(10.5 if fraud else 8.5, 1.2)),
            card_hits=rng.choice([1, 2, 4, 6, 9] if fraud else [1, 1, 1, 2]),
            ip_hits=rng.randint(3, 15) if fraud else rng.randint(1, 3),
            new_device=rng.random() < (0.7 if fraud else 0.15),
            country_changed=rng.random() < (0.4 if fraud else 0.03),
            timezone_changed=rng.random() < (0.4 if fraud else 0.05),
            language_changed=rng.random() < (0.3 if fraud else 0.05))
        rows.append(feature_vector(r))
        labels.append(int(fraud))
    return np.array(rows), np.array(labels)


def main() -> None:
    x, y = synthetic(random.Random(SEED))
    x_tr, x_te, y_tr, y_te = train_test_split(x, y, test_size=0.25, random_state=SEED, stratify=y)
    scaler = StandardScaler().fit(x_tr)
    clf = LogisticRegression(max_iter=1000).fit(scaler.transform(x_tr), y_tr)
    auc = roc_auc_score(y_te, clf.predict_proba(scaler.transform(x_te))[:, 1])
    MODEL_PATH.write_text(json.dumps({
        "version": "risk-lr-v1",
        "features": list(FEATURES),
        "bias": float(clf.intercept_[0]),
        "weights": clf.coef_[0].tolist(),
        "means": scaler.mean_.tolist(),
        "scales": scaler.scale_.tolist(),
        "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "trainingData": f"synthetic (seed {SEED}, {ROWS} rows, {FRAUD_RATE:.0%} positive) - NOT real fraud labels",
        "holdoutAuc": round(float(auc), 4),
    }, indent=2) + "\n")
    print(f"wrote {MODEL_PATH.name}: holdout AUC {auc:.4f}")


if __name__ == "__main__":
    main()
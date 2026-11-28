# Mirrors risk-service RiskEngine rule ids. Used for templates and to detect hallucinated rule ids.
RULES = {
    "high_amount": "amount above the $10,000 high-value threshold",
    "card_velocity": "card used more than 3 times within the velocity window",
    "card_velocity_block": "card used more than 5 times within the velocity window",
    "ip_velocity": "more than 8 attempts from the same IP within the velocity window",
    "new_device": "first payment seen from this device",
    "country_changed": "device country differs from its history",
    "timezone_changed": "device timezone differs from its history",
    "language_changed": "browser language differs from its history",
    "ml_high_risk": "ML probability at or above the 0.85 review threshold",
    "risk_unavailable": "risk service unavailable; payment-service fallback rule applied",
}
REVIEW_AT, BLOCK_AT, ML_REVIEW_AT = 30, 60, 0.85
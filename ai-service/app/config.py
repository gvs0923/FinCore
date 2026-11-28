import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    # Any OpenAI-compatible endpoint, e.g. Ollama: http://localhost:11434/v1. Empty = templates only.
    llm_base_url: str = os.getenv("FINCORE_LLM_BASE_URL", "")
    llm_model: str = os.getenv("FINCORE_LLM_MODEL", "llama3.1:8b")
    llm_api_key: str = os.getenv("FINCORE_LLM_API_KEY", "")
    llm_timeout_s: float = float(os.getenv("FINCORE_LLM_TIMEOUT_S", "30"))
    kafka_bootstrap: str = os.getenv("FINCORE_KAFKA_BOOTSTRAP", "localhost:9092")
    kafka_enabled: bool = os.getenv("FINCORE_AI_KAFKA_ENABLED", "true") == "true"
    event_db: Path = Path(os.getenv("FINCORE_AI_EVENT_DB", str(Path.home() / ".fincore" / "ai-events.db")))
    event_retention_days: int = int(os.getenv("FINCORE_AI_EVENT_RETENTION_DAYS", "14"))
    jaeger_url: str = os.getenv("FINCORE_JAEGER_URL", "http://localhost:16686")


settings = Settings()
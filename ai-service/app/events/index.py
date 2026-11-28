import re
import sqlite3
import threading
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

_SCHEMA = """
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY,
    topic TEXT NOT NULL,
    kafka_partition INTEGER NOT NULL,
    kafka_offset INTEGER NOT NULL,
    payment_id TEXT,
    trace_id TEXT,
    occurred_at TEXT NOT NULL,
    body TEXT NOT NULL,
    UNIQUE (topic, kafka_partition, kafka_offset)
);
CREATE INDEX IF NOT EXISTS idx_events_payment ON events (payment_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_events_time ON events (occurred_at);
CREATE VIRTUAL TABLE IF NOT EXISTS events_fts USING fts5(body);
CREATE TRIGGER IF NOT EXISTS events_ai AFTER INSERT ON events BEGIN
    INSERT INTO events_fts (rowid, body) VALUES (new.id, new.topic || ' ' || new.body);
END;
CREATE TRIGGER IF NOT EXISTS events_ad AFTER DELETE ON events BEGIN
    DELETE FROM events_fts WHERE rowid = old.id;
END;
"""
_STOP = {"why", "did", "the", "what", "was", "for", "and", "how", "many", "with", "this", "that",
         "payment", "payments", "are", "were", "has", "have", "from", "show", "tell"}


@dataclass(frozen=True)
class IndexedEvent:
    topic: str
    partition: int
    offset: int
    payment_id: str | None
    trace_id: str | None
    occurred_at: str
    body: str


class EventIndex:
    """ai-service's own derived copy of the event log: rebuildable by replaying Kafka, never a source of truth."""

    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self._db = sqlite3.connect(path, check_same_thread=False)
        self._db.executescript(_SCHEMA)
        self._lock = threading.Lock()

    def add(self, e: IndexedEvent) -> bool:
        with self._lock, self._db:
            cur = self._db.execute(
                "INSERT OR IGNORE INTO events (topic, kafka_partition, kafka_offset, payment_id, trace_id, "
                "occurred_at, body) VALUES (?, ?, ?, ?, ?, ?, ?)",
                (e.topic, e.partition, e.offset, e.payment_id, e.trace_id, e.occurred_at, e.body))
            return cur.rowcount == 1

    def _rows(self, sql: str, params: tuple) -> list[IndexedEvent]:
        with self._lock:
            return [IndexedEvent(*row) for row in self._db.execute(sql, params).fetchall()]

    def for_payment(self, payment_id: str, limit: int = 25) -> list[IndexedEvent]:
        return self._rows(
            "SELECT topic, kafka_partition, kafka_offset, payment_id, trace_id, occurred_at, body "
            "FROM events WHERE payment_id = ? ORDER BY occurred_at, id LIMIT ?", (payment_id, limit))

    def search(self, text: str, limit: int = 8) -> list[IndexedEvent]:
        terms = [t for t in dict.fromkeys(re.findall(r"[a-z0-9]{3,}", text.lower())) if t not in _STOP][:8]
        if not terms:
            return []
        return self._rows(
            "SELECT e.topic, e.kafka_partition, e.kafka_offset, e.payment_id, e.trace_id, e.occurred_at, e.body "
            "FROM events_fts JOIN events e ON e.id = events_fts.rowid "
            "WHERE events_fts MATCH ? ORDER BY bm25(events_fts) LIMIT ?",
            (" OR ".join(f'"{t}"' for t in terms), limit))

    def prune(self, days: int) -> None:
        cutoff = (datetime.now(timezone.utc) - timedelta(days=days)).isoformat(timespec="milliseconds")
        with self._lock, self._db:
            self._db.execute("DELETE FROM events WHERE occurred_at < ?", (cutoff,))

    def count(self) -> int:
        with self._lock:
            return self._db.execute("SELECT count(*) FROM events").fetchone()[0]

    def close(self) -> None:
        self._db.close()
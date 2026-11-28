import asyncio
import json
import logging
import re
from datetime import datetime, timezone

from aiokafka import AIOKafkaConsumer
from aiokafka.errors import KafkaError

from app.events.index import EventIndex, IndexedEvent
from app.redaction import redact

log = logging.getLogger("fincore.ai.consumer")

# Anchored, and "-" is excluded: never re-ingest our own or anyone's "-dlt" topics.
TOPIC_PATTERN = r"^(payment|risk|ledger|settlement)\.[a-z_]+$"
_UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")


def to_event(msg) -> IndexedEvent:
    raw = msg.value.decode("utf-8", "replace") if msg.value else ""
    try:
        payload = json.loads(raw)
        pid = payload.get("paymentId") if isinstance(payload, dict) else None
    except ValueError:
        pid = None
    pid = pid.lower() if isinstance(pid, str) and _UUID.fullmatch(pid.lower()) else None
    traceparent = next((v.decode("ascii", "replace") for k, v in (msg.headers or ()) if k == "traceparent" and v), "")
    parts = traceparent.split("-")
    trace_id = parts[1] if len(parts) == 4 and re.fullmatch(r"[0-9a-f]{32}", parts[1]) else None
    at = datetime.fromtimestamp(msg.timestamp / 1000, tz=timezone.utc).isoformat(timespec="milliseconds")
    return IndexedEvent(msg.topic, msg.partition, msg.offset, pid, trace_id, at, redact(raw)[:4000])


async def run_consumer(index: EventIndex, bootstrap: str) -> None:
    """At-least-once: commit after insert; the (topic, partition, offset) unique key makes redelivery a no-op."""
    backoff = 1
    while True:
        consumer = AIOKafkaConsumer(bootstrap_servers=bootstrap, group_id="ai-service",
                                    enable_auto_commit=False, auto_offset_reset="earliest",
                                    metadata_max_age_ms=30_000)
        consumer.subscribe(pattern=TOPIC_PATTERN)
        try:
            await consumer.start()
            backoff = 1
            async for msg in consumer:
                index.add(to_event(msg))
                await consumer.commit()
        except KafkaError as e:
            log.warning("Kafka unavailable (%s); event indexing paused for %ss", type(e).__name__, backoff)
        finally:
            await consumer.stop()
        await asyncio.sleep(backoff)
        backoff = min(backoff * 2, 60)
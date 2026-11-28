import re

import httpx


class TraceClient:
    def __init__(self, base_url: str, timeout_s: float = 2.0):
        self._base = base_url.rstrip("/")
        self._timeout = timeout_s

    async def summarize(self, trace_id: str) -> str | None:
        if not re.fullmatch(r"[0-9a-f]{32}", trace_id):
            return None
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                res = await client.get(f"{self._base}/api/traces/{trace_id}")
            if res.status_code != 200:
                return None
            data = res.json().get("data") or []
        except (httpx.HTTPError, ValueError):
            return None
        if not data:
            return None
        trace = data[0]
        processes = trace.get("processes", {})
        spans = sorted(trace.get("spans", []), key=lambda s: s.get("startTime", 0))
        lines = [f"trace {trace_id}: {len(spans)} spans"]
        for span in spans[:20]:
            tags = {t.get("key"): t.get("value") for t in span.get("tags", [])}
            service = processes.get(span.get("processID"), {}).get("serviceName", "?")
            status = tags.get("http.response.status_code") or tags.get("http.status_code")
            error = tags.get("error") in (True, "true") or tags.get("otel.status_code") == "ERROR"
            lines.append(f"{service} {span.get('operationName')} {span.get('duration', 0) / 1000:.0f}ms"
                         + (f" status={status}" if status else "") + (" ERROR" if error else ""))
            for entry in span.get("logs", [])[:2]:
                fields = {f.get("key"): f.get("value") for f in entry.get("fields", [])}
                if message := fields.get("exception.message"):
                    lines.append(f"  exception: {str(message)[:200]}")
        return "\n".join(lines)
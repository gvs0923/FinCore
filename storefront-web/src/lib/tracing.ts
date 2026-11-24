// W3C Trace Context: mint a traceparent per request so browser calls join
// the backend's distributed traces. Sampled flag on — sampling decisions
// still belong to the collector.
function hex(bytes: number): string {
  const buf = new Uint8Array(bytes);
  crypto.getRandomValues(buf);
  return Array.from(buf, (b) => b.toString(16).padStart(2, "0")).join("");
}

export function traceHeaders(): { traceparent: string } {
  return { traceparent: `00-${hex(16)}-${hex(8)}-01` };
}

export function traceIdOf(headers: { traceparent: string }): string {
  return headers.traceparent.split("-")[1];
}

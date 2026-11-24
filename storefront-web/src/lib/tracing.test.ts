import { describe, expect, it } from "vitest";
import { traceHeaders } from "./tracing";

describe("W3C tracing", () => {
  it("uses fresh cryptographic trace and parent IDs for each request", () => {
    const first = traceHeaders().traceparent;
    const second = traceHeaders().traceparent;
    expect(first).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
    expect(first).not.toBe(second);
    expect(first.split("-")[1]).not.toBe("0".repeat(32));
    expect(first.split("-")[2]).not.toBe("0".repeat(16));
  });
});

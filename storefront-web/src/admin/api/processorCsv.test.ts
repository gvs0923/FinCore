import { describe, expect, it } from "vitest";
import { parseProcessorCsv } from "./processorCsv";

const header = "processorRef,currency,amountMinor,feeMinor\n";

describe("processor CSV", () => {
  it("parses integer money, CRLF and quoted references", () => {
    expect(parseProcessorCsv('\uFEFF' + header.replace(/\n/g, '\r\n') + '"capture,""one""",USD,10000,320\r\n'))
      .toEqual([{ processorRef: 'capture,"one"', currency: "USD", amountMinor: 10000, feeMinor: 320 }]);
  });
  it.each([
    "wrong,header\nref,USD,1,1",
    header,
    header + "ref,EUR,100,1",
    header + "ref,USD,1.1,1",
    header + "ref,USD,-1,1",
    header + "ref,USD,9007199254740992,1",
    header + "ref,USD,100,1\nref,USD,100,1",
    header + '"unclosed,USD,100,1',
    header + '"ref"junk,USD,100,1',
  ])("rejects malformed or unsafe reports: %s", csv => {
    expect(() => parseProcessorCsv(csv)).toThrow();
  });
});

import { describe, it, expect } from "vitest";
import { checkoutFormSchema } from "./checkoutSchema";

/** Helper — parses and returns either the data or the first error message. */
function parse(input: Record<string, string>) {
  const result = checkoutFormSchema.safeParse(input);
  if (result.success) return { ok: true, data: result.data } as const;
  const msg = result.error.issues[0]?.message ?? "unknown";
  return { ok: false, msg } as const;
}

const VALID = { cardNumber: "4242424242424242", expiry: "12/28", cvv: "123" };

describe("checkoutFormSchema", () => {
  // ---- happy path ----

  it("accepts a valid Visa test card", () => {
    expect(parse(VALID).ok).toBe(true);
  });

  it("accepts 13-digit card numbers that pass Luhn", () => {
    expect(parse({ ...VALID, cardNumber: "4000000000006" }).ok).toBe(true);
  });

  it("accepts 4-digit CVV (Amex style)", () => {
    expect(parse({ ...VALID, cvv: "1234" }).ok).toBe(true);
  });

  // ---- card number validation ----

  it("rejects non-numeric card number", () => {
    const r = parse({ ...VALID, cardNumber: "4242abcd42424242" });
    expect(r.ok).toBe(false);
  });

  it("rejects card number shorter than 13 digits", () => {
    const r = parse({ ...VALID, cardNumber: "424242424242" }); // 12 digits
    expect(r.ok).toBe(false);
  });

  it("rejects card number longer than 19 digits", () => {
    const r = parse({ ...VALID, cardNumber: "42424242424242424242" }); // 20 digits
    expect(r.ok).toBe(false);
  });

  it("rejects card number that fails Luhn check", () => {
    const r = parse({ ...VALID, cardNumber: "4242424242424241" }); // last digit wrong
    expect(r.ok).toBe(false);
    expect(r.ok === false && r.msg).toBe("Card number is invalid");
  });

  // ---- expiry validation ----

  it("rejects expiry without slash", () => {
    expect(parse({ ...VALID, expiry: "1228" }).ok).toBe(false);
  });

  it("rejects month 00", () => {
    expect(parse({ ...VALID, expiry: "00/28" }).ok).toBe(false);
  });

  it("rejects month 13", () => {
    expect(parse({ ...VALID, expiry: "13/28" }).ok).toBe(false);
  });

  it("accepts month 01 through 12", () => {
    for (const mm of ["01", "06", "12"]) {
      expect(parse({ ...VALID, expiry: `${mm}/28` }).ok).toBe(true);
    }
  });

  // ---- CVV validation ----

  it("rejects 2-digit CVV", () => {
    expect(parse({ ...VALID, cvv: "12" }).ok).toBe(false);
  });

  it("rejects 5-digit CVV", () => {
    expect(parse({ ...VALID, cvv: "12345" }).ok).toBe(false);
  });

  it("rejects non-numeric CVV", () => {
    expect(parse({ ...VALID, cvv: "12a" }).ok).toBe(false);
  });

  // ---- missing fields ----

  it("rejects when cardNumber is missing", () => {
    expect(parse({ expiry: VALID.expiry, cvv: VALID.cvv })).toMatchObject({
      ok: false,
    });
  });

  it("rejects when expiry is missing", () => {
    expect(
      parse({ cardNumber: VALID.cardNumber, cvv: VALID.cvv }),
    ).toMatchObject({ ok: false });
  });

  it("rejects when cvv is missing", () => {
    expect(
      parse({ cardNumber: VALID.cardNumber, expiry: VALID.expiry }),
    ).toMatchObject({ ok: false });
  });
});

import { z } from "zod";
import type { components } from "../../api/schema";

z.config({ jitless: true });

const rowSchema = z.object({
  processorRef: z.string().min(1).max(255),
  currency: z.literal("USD"),
  amountMinor: z.string().regex(/^-?\d+$/).transform(Number).refine(Number.isSafeInteger),
  feeMinor: z.string().regex(/^\d+$/).transform(Number).refine(Number.isSafeInteger),
});

export function parseProcessorCsv(csv: string, allowNegativeAmounts = false): Required<components["schemas"]["ProcessorRow"]>[] {
  if (csv.length > 2_000_000) throw new Error("Report exceeds 2 MB");
  const records: string[][] = [];
  let fields: string[] = [], field = "", quoted = false, closed = false;
  const endField = () => { fields.push(field); field = ""; closed = false; };
  const endRow = () => { endField(); records.push(fields); fields = []; };
  const input = csv.replace(/^\uFEFF/, "").replace(/\r\n/g, "\n");
  for (let i = 0; i < input.length; i++) {
    const c = input[i];
    if (quoted) {
      if (c === '"' && input[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') { quoted = false; closed = true; }
      else field += c;
    } else if (c === ",") endField();
    else if (c === "\n") endRow();
    else if (c === '"' && field === "" && !closed) quoted = true;
    else {
      if (closed || c === '"' || c === "\r") throw new Error("Malformed CSV");
      field += c;
    }
  }
  if (quoted) throw new Error("Unclosed CSV quote");
  if (field || fields.length || closed) endRow();
  const header = ["processorRef", "currency", "amountMinor", "feeMinor"];
  if (records.shift()?.join(",") !== header.join(",")) {
    throw new Error(`CSV header must be ${header.join(",")}`);
  }
  if (records.length === 0 || records.length > 10_000) throw new Error("Report must contain 1–10,000 rows");
  const refs = new Set<string>();
  return records.map((record, i) => {
    if (record.length !== 4) throw new Error(`Invalid column count on row ${i + 2}`);
    const result = rowSchema.safeParse(Object.fromEntries(header.map((h, n) => [h, record[n]])));
    if (!result.success || result.data.amountMinor === 0
        || (!allowNegativeAmounts && result.data.amountMinor < 0)) {
      throw new Error(`Invalid values on row ${i + 2}`);
    }
    if (refs.has(result.data.processorRef)) throw new Error(`Duplicate reference on row ${i + 2}`);
    refs.add(result.data.processorRef);
    return result.data;
  });
}

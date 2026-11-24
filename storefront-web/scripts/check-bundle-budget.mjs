import { readFileSync } from "node:fs";
import { gzipSync } from "node:zlib";

const BUDGET_KB = { "index.html": 145, "admin.html": 241 };
const manifest = JSON.parse(readFileSync("dist/.vite/manifest.json", "utf8"));

function closure(key, seen = new Set()) {
  if (seen.has(key)) return seen;
  if (!manifest[key]) throw new Error(`Missing manifest entry: ${key}`);
  seen.add(key);
  for (const dep of manifest[key].imports ?? []) closure(dep, seen);
  return seen;
}

let failed = false;
for (const [entry, budget] of Object.entries(BUDGET_KB)) {
  if (!manifest[entry]?.isEntry) throw new Error(`Missing HTML entry: ${entry}`);
  const files = new Set([...closure(entry)].map(key => manifest[key].file));
  const bytes = [...files].reduce((sum, file) => sum + gzipSync(readFileSync(`dist/${file}`)).length, 0);
  const kb = bytes / 1024;
  console.log(`${entry}: ${kb.toFixed(1)} KB gzip (budget ${budget} KB)`);
  if (kb > budget) failed = true;
}
process.exitCode = failed ? 1 : 0;
